package com.archdox.cloud.legal.application;

import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalArticle;
import com.archdox.cloud.legal.domain.LegalArticleDiff;
import com.archdox.cloud.legal.domain.LegalArticleVersion;
import com.archdox.cloud.legal.domain.LegalChangeSet;
import com.archdox.cloud.legal.domain.LegalSource;
import com.archdox.cloud.legal.domain.LegalSyncRun;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleDiffRepository;
import com.archdox.cloud.legal.infra.LegalArticleRepository;
import com.archdox.cloud.legal.infra.LegalArticleVersionRepository;
import com.archdox.cloud.legal.infra.LegalChangeSetRepository;
import com.archdox.cloud.legal.infra.LegalSourceRepository;
import com.archdox.cloud.legal.infra.LegalSyncRunRepository;
import com.archdox.cloud.legal.infra.LegalVersionRepository;
import com.archdox.cloud.legal.domain.LegalVersion;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegalCorpusSyncService {
    private final List<LegalSourceClient> sourceClients;
    private final LegalSourceRepository sourceRepository;
    private final LegalActRepository actRepository;
    private final LegalVersionRepository versionRepository;
    private final LegalArticleRepository articleRepository;
    private final LegalArticleVersionRepository articleVersionRepository;
    private final LegalChangeSetRepository changeSetRepository;
    private final LegalArticleDiffRepository articleDiffRepository;
    private final LegalSyncRunRepository syncRunRepository;
    private final LegalTextNormalizer normalizer;
    private final LegalArticleHashService hashService;
    private final LegalDiffService diffService;
    private final LegalChangeDigestService changeDigestService;
    private final OperationEventService operationEventService;
    private final Executor legalSyncFetchExecutor;

    public LegalCorpusSyncService(
            List<LegalSourceClient> sourceClients,
            LegalSourceRepository sourceRepository,
            LegalActRepository actRepository,
            LegalVersionRepository versionRepository,
            LegalArticleRepository articleRepository,
            LegalArticleVersionRepository articleVersionRepository,
            LegalChangeSetRepository changeSetRepository,
            LegalArticleDiffRepository articleDiffRepository,
            LegalSyncRunRepository syncRunRepository,
            LegalTextNormalizer normalizer,
            LegalArticleHashService hashService,
            LegalDiffService diffService,
            LegalChangeDigestService changeDigestService,
            OperationEventService operationEventService,
            @Qualifier("legalSyncFetchExecutor") Executor legalSyncFetchExecutor
    ) {
        this.sourceClients = List.copyOf(sourceClients);
        this.sourceRepository = sourceRepository;
        this.actRepository = actRepository;
        this.versionRepository = versionRepository;
        this.articleRepository = articleRepository;
        this.articleVersionRepository = articleVersionRepository;
        this.changeSetRepository = changeSetRepository;
        this.articleDiffRepository = articleDiffRepository;
        this.syncRunRepository = syncRunRepository;
        this.normalizer = normalizer;
        this.hashService = hashService;
        this.diffService = diffService;
        this.changeDigestService = changeDigestService;
        this.operationEventService = operationEventService;
        this.legalSyncFetchExecutor = legalSyncFetchExecutor;
    }

    @Transactional
    public LegalSyncRun createRun(String triggerType, String sourceCode, Long actorUserId) {
        return syncRunRepository.saveAndFlush(new LegalSyncRun(
                triggerType,
                sourceCode == null || sourceCode.isBlank() ? FakeLegalSourceClient.DEFAULT_SOURCE_CODE : sourceCode.trim(),
                actorUserId,
                OffsetDateTime.now()));
    }

    public LegalSourceSnapshot fetchSnapshot(String sourceCode) {
        return sourceClients.stream()
                .filter(client -> client.supports(sourceCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No legal source client supports source: " + sourceCode))
                .fetch(sourceCode);
    }

    public CompletableFuture<LegalSourceSnapshot> fetchSnapshotAsync(String sourceCode) {
        return CompletableFuture.supplyAsync(() -> fetchSnapshot(sourceCode), legalSyncFetchExecutor);
    }

    @Transactional
    public LegalSyncResult applySnapshot(Long runId, LegalSourceSnapshot snapshot) {
        var now = OffsetDateTime.now();
        var run = syncRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Legal sync run not found: " + runId));
        var source = upsertSource(snapshot, now);

        int actsSeen = 0;
        int versionsCreated = 0;
        int changeSetsCreated = 0;
        int articleDiffsCreated = 0;

        for (var actSnapshot : snapshot.acts()) {
            actsSeen++;
            var act = upsertAct(source.id(), actSnapshot, now);
            var existingVersion = versionRepository.findByActIdAndSourceVersionKey(
                    act.id(),
                    required(actSnapshot.sourceVersionKey(), "sourceVersionKey"));
            if (existingVersion.isPresent()) {
                continue;
            }

            var materials = articleMaterials(actSnapshot);
            var version = versionRepository.save(new LegalVersion(
                    act.id(),
                    actSnapshot.sourceVersionKey(),
                    actSnapshot.promulgationDate(),
                    actSnapshot.effectiveDate(),
                    actSnapshot.sourceUrl(),
                    actHash(materials),
                    actSnapshot.metadata(),
                    now));
            versionsCreated++;

            var currentVersions = saveArticleVersions(act.id(), version, actSnapshot, materials, now);
            var previousVersion = versionRepository
                    .findFirstByActIdAndIdNotOrderByCapturedAtDescIdDesc(act.id(), version.id())
                    .orElse(null);
            var previousArticleVersions = previousVersion == null
                    ? List.<LegalArticleVersion>of()
                    : articleVersionRepository.findByLegalVersionId(previousVersion.id());

            var diffDrafts = diffService.diff(previousArticleVersions, currentVersions);
            if (!diffDrafts.isEmpty()) {
                var changeSet = changeSetRepository.save(new LegalChangeSet(
                        act.id(),
                        runId,
                        previousVersion == null ? null : previousVersion.id(),
                        version.id(),
                        actSnapshot.effectiveDate(),
                        changeSummary(previousVersion, diffDrafts.size()),
                        Map.of(
                                "sourceCode", snapshot.sourceCode(),
                                "actCode", act.actCode(),
                                "sourceVersionKey", actSnapshot.sourceVersionKey()),
                        now));
                changeSetsCreated++;
                var diffs = diffDrafts.stream()
                        .map(draft -> new LegalArticleDiff(
                                changeSet.id(),
                                draft.articleId(),
                                draft.articleKey(),
                                draft.articleNo(),
                                draft.changeType(),
                                draft.beforeArticleVersionId(),
                                draft.afterArticleVersionId(),
                                draft.beforeHash(),
                                draft.afterHash(),
                                draft.summary(),
                                now))
                        .toList();
                articleDiffRepository.saveAll(diffs);
                changeDigestService.ensureDeterministicDigest(changeSet, act, diffs, now);
                articleDiffsCreated += diffs.size();
            }
        }

        var result = new LegalSyncResult(runId, actsSeen, versionsCreated, changeSetsCreated, articleDiffsCreated);
        run.complete(result.toSummaryJson(), now);
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                "LEGAL_SYNC_COMPLETED",
                "legal-sync",
                "run:" + runId,
                "LEGAL_SYNC_RUN",
                runId,
                "Legal corpus sync completed.",
                result.toSummaryJson());
        return result;
    }

    @Transactional
    public void markRunFailed(Long runId, String failureCode) {
        markRunFailed(runId, failureCode, null);
    }

    @Transactional
    public void markRunFailed(Long runId, String failureCode, String failureMessage) {
        var now = OffsetDateTime.now();
        syncRunRepository.findById(runId).ifPresent(run -> run.fail(failureCode, failureMessage, now));
        var safeFailureCode = failureCode == null ? "UNKNOWN" : failureCode;
        var safeFailureMessage = failureMessage == null || failureMessage.isBlank()
                ? "No failure message was captured."
                : failureMessage;
        operationEventService.record(
                null,
                OperationEventSeverity.ERROR,
                "LEGAL_SYNC_FAILED",
                "legal-sync",
                "run:" + runId,
                "LEGAL_SYNC_RUN",
                runId,
                "Legal corpus sync failed: " + safeFailureMessage,
                Map.of(
                        "failureCode", safeFailureCode,
                        "failureMessage", safeFailureMessage));
    }

    private LegalSource upsertSource(LegalSourceSnapshot snapshot, OffsetDateTime now) {
        return sourceRepository.findByCode(required(snapshot.sourceCode(), "sourceCode"))
                .map(source -> {
                    source.update(snapshot.sourceType(), snapshot.displayName(), snapshot.baseUrl(), snapshot.metadata(), now);
                    return source;
                })
                .orElseGet(() -> sourceRepository.save(new LegalSource(
                        snapshot.sourceCode(),
                        snapshot.sourceType(),
                        snapshot.displayName(),
                        snapshot.baseUrl(),
                        snapshot.metadata(),
                        now)));
    }

    private LegalAct upsertAct(Long sourceId, LegalActSnapshot snapshot, OffsetDateTime now) {
        return actRepository.findBySourceIdAndActCode(sourceId, required(snapshot.actCode(), "actCode"))
                .map(act -> {
                    act.update(snapshot.actName(), snapshot.actType(), snapshot.jurisdiction(), snapshot.sourceLawId(), now);
                    return act;
                })
                .orElseGet(() -> actRepository.save(new LegalAct(
                        sourceId,
                        snapshot.actCode(),
                        snapshot.actName(),
                        snapshot.actType(),
                        snapshot.jurisdiction(),
                        snapshot.sourceLawId(),
                        now)));
    }

    private List<ArticleMaterial> articleMaterials(LegalActSnapshot snapshot) {
        return snapshot.articles().stream()
                .sorted(Comparator.comparingInt(LegalArticleSnapshot::sortOrder)
                        .thenComparing(LegalArticleSnapshot::articleKey))
                .map(article -> {
                    var normalized = normalizer.normalize(article.articleText());
                    return new ArticleMaterial(article, normalized, hashService.sha256(normalized));
                })
                .toList();
    }

    private String actHash(List<ArticleMaterial> materials) {
        var combined = materials.stream()
                .map(material -> material.article().articleKey() + "=" + material.hash())
                .reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right);
        return hashService.sha256(combined);
    }

    private List<LegalArticleVersion> saveArticleVersions(
            Long actId,
            LegalVersion version,
            LegalActSnapshot actSnapshot,
            List<ArticleMaterial> materials,
            OffsetDateTime now
    ) {
        var saved = new ArrayList<LegalArticleVersion>();
        for (var material : materials) {
            var articleSnapshot = material.article();
            var article = articleRepository.findByActIdAndArticleKey(actId, required(articleSnapshot.articleKey(), "articleKey"))
                    .map(existing -> {
                        existing.update(
                                articleSnapshot.articleNo(),
                                articleSnapshot.articleTitle(),
                                articleSnapshot.parentArticleKey(),
                                articleSnapshot.sortOrder(),
                                now);
                        return existing;
                    })
                    .orElseGet(() -> articleRepository.save(new LegalArticle(
                            actId,
                            articleSnapshot.articleKey(),
                            articleSnapshot.articleNo(),
                            articleSnapshot.articleTitle(),
                            articleSnapshot.parentArticleKey(),
                            articleSnapshot.sortOrder(),
                            now)));
            saved.add(articleVersionRepository.save(new LegalArticleVersion(
                    article.id(),
                    version.id(),
                    articleSnapshot.articleKey(),
                    articleSnapshot.articleNo(),
                    articleSnapshot.articleTitle(),
                    articleSnapshot.articleText(),
                    material.normalizedText(),
                    material.hash(),
                    actSnapshot.effectiveDate(),
                    articleSnapshot.metadata(),
                    now)));
        }
        return saved;
    }

    private String changeSummary(LegalVersion previousVersion, int diffCount) {
        if (previousVersion == null) {
            return "Initial legal corpus snapshot captured. Article changes: " + diffCount;
        }
        return "Legal corpus version changed. Article changes: " + diffCount;
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private record ArticleMaterial(
            LegalArticleSnapshot article,
            String normalizedText,
            String hash
    ) {
    }
}

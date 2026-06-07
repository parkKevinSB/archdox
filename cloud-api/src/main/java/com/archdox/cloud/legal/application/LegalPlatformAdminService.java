package com.archdox.cloud.legal.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.domain.LegalChangeDigestSource;
import com.archdox.cloud.legal.dto.LegalChangeSetResponse;
import com.archdox.cloud.legal.dto.LegalChangeDigestResponse;
import com.archdox.cloud.legal.dto.LegalDigestAiDraftResponse;
import com.archdox.cloud.legal.dto.LegalDigestRefreshResponse;
import com.archdox.cloud.legal.dto.LegalOpenApiStatusResponse;
import com.archdox.cloud.legal.dto.LegalSyncRunResponse;
import com.archdox.cloud.legal.event.LegalSyncRequested;
import com.archdox.cloud.legal.flow.LegalSyncFlowFactory;
import com.archdox.cloud.legal.flow.LegalSyncWorker;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleDiffRepository;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import com.archdox.cloud.legal.infra.LegalChangeSetRepository;
import com.archdox.cloud.legal.infra.LegalSyncRunRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.worker.ArchDoxWorkerServiceWorker;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerActionExecutionStatus;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import com.archdox.worker.flow.ArchDoxWorkerExecutionFlowFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class LegalPlatformAdminService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final PlatformAdminService platformAdminService;
    private final LegalCorpusSyncService syncService;
    private final LegalSyncFlowFactory flowFactory;
    private final LegalSyncWorker worker;
    private final LegalSyncRunRepository syncRunRepository;
    private final LegalActRepository actRepository;
    private final LegalChangeSetRepository changeSetRepository;
    private final LegalArticleDiffRepository articleDiffRepository;
    private final LegalChangeDigestRepository changeDigestRepository;
    private final LegalUpdateReadService updateReadService;
    private final LegalSyncProperties legalSyncProperties;
    private final LegalChangeDigestService changeDigestService;
    private final ArchDoxWorkerExecutionFlowFactory workerFlowFactory;
    private final ArchDoxWorkerServiceWorker workerServiceWorker;
    private final LegalDigestAiProperties legalDigestAiProperties;

    public LegalPlatformAdminService(
            PlatformAdminService platformAdminService,
            LegalCorpusSyncService syncService,
            LegalSyncFlowFactory flowFactory,
            LegalSyncWorker worker,
            LegalSyncRunRepository syncRunRepository,
            LegalActRepository actRepository,
            LegalChangeSetRepository changeSetRepository,
            LegalArticleDiffRepository articleDiffRepository,
            LegalChangeDigestRepository changeDigestRepository,
            LegalUpdateReadService updateReadService,
            LegalSyncProperties legalSyncProperties,
            LegalChangeDigestService changeDigestService,
            ArchDoxWorkerExecutionFlowFactory workerFlowFactory,
            ArchDoxWorkerServiceWorker workerServiceWorker,
            LegalDigestAiProperties legalDigestAiProperties
    ) {
        this.platformAdminService = platformAdminService;
        this.syncService = syncService;
        this.flowFactory = flowFactory;
        this.worker = worker;
        this.syncRunRepository = syncRunRepository;
        this.actRepository = actRepository;
        this.changeSetRepository = changeSetRepository;
        this.articleDiffRepository = articleDiffRepository;
        this.changeDigestRepository = changeDigestRepository;
        this.updateReadService = updateReadService;
        this.legalSyncProperties = legalSyncProperties;
        this.changeDigestService = changeDigestService;
        this.workerFlowFactory = workerFlowFactory;
        this.workerServiceWorker = workerServiceWorker;
        this.legalDigestAiProperties = legalDigestAiProperties;
    }

    public LegalOpenApiStatusResponse openApiStatus(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        var openApi = legalSyncProperties.getOpenApi();
        var activeTargets = activeTargets(openApi);
        var ocConfigured = openApi.getOc() != null && !openApi.getOc().isBlank();
        return new LegalOpenApiStatusResponse(
                openApi.isEnabled(),
                ocConfigured,
                openApi.isEnabled() && ocConfigured && !activeTargets.isEmpty(),
                openApi.getSourceCode(),
                openApi.getBaseUrl(),
                openApi.getUserAgent(),
                openApi.getRequestTimeoutMs(),
                openApi.getRequestIntervalMs(),
                openApi.getMaxAttempts(),
                activeTargets.size(),
                activeTargets.size() * 2,
                activeTargets.stream()
                        .map(target -> new LegalOpenApiStatusResponse.TargetResponse(
                                target.getTarget(),
                                target.getQuery(),
                                target.getExpectedName(),
                                target.getActCode(),
                                target.getActType()))
                        .toList());
    }

    public LegalSyncRunResponse startFakeSync(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        var run = syncService.createRun("PLATFORM_ADMIN_MANUAL", FakeLegalSourceClient.DEFAULT_SOURCE_CODE, principal.userId());
        worker.submit(flowFactory.create(new LegalSyncRequested(run.id(), run.sourceCode())));
        return toRunResponse(run);
    }

    public LegalSyncRunResponse startOpenDataSync(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        requireOpenDataSyncReady();
        var run = syncService.createRun("PLATFORM_ADMIN_MANUAL", LawOpenDataLegalSourceClient.SOURCE_CODE, principal.userId());
        worker.submit(flowFactory.create(new LegalSyncRequested(run.id(), run.sourceCode())));
        return toRunResponse(run);
    }

    public List<LegalSyncRunResponse> syncRuns(UserPrincipal principal, String sourceCode, Integer limit) {
        platformAdminService.requirePlatformAdmin(principal);
        var page = PageRequest.of(0, limit(limit));
        var runs = sourceCode == null || sourceCode.isBlank()
                ? syncRunRepository.findAllByOrderByStartedAtDescIdDesc(page)
                : syncRunRepository.findBySourceCodeOrderByStartedAtDescIdDesc(sourceCode.trim(), page);
        return runs.stream().map(this::toRunResponse).toList();
    }

    public List<LegalChangeSetResponse> changeSets(UserPrincipal principal, Integer limit) {
        platformAdminService.requirePlatformAdmin(principal);
        return changeSetRepository.findAllByOrderByDetectedAtDescIdDesc(PageRequest.of(0, limit(limit)))
                .stream()
                .map(changeSet -> new LegalChangeSetResponse(
                        changeSet.id(),
                        changeSet.actId(),
                        changeSet.syncRunId(),
                        changeSet.previousVersionId(),
                        changeSet.newVersionId(),
                        changeSet.status(),
                        changeSet.effectiveDate(),
                        changeSet.detectedAt(),
                        changeSet.summary()))
                .toList();
    }

    public List<LegalChangeDigestResponse> changeDigests(UserPrincipal principal, Integer limit) {
        platformAdminService.requirePlatformAdmin(principal);
        return changeDigestRepository.findAllExcludingSourceCode(
                        FakeLegalSourceClient.DEFAULT_SOURCE_CODE,
                        PageRequest.of(0, limit(limit)))
                .stream()
                .map(updateReadService::toResponse)
                .toList();
    }

    public LegalDigestRefreshResponse refreshDeterministicDigests(UserPrincipal principal, Integer limit) {
        platformAdminService.requirePlatformAdmin(principal);
        var now = OffsetDateTime.now();
        var inspected = 0;
        var created = 0;
        var refreshed = 0;
        var skippedAi = 0;
        var skippedMissingActs = 0;
        var changeSets = changeSetRepository.findAllByOrderByDetectedAtDescIdDesc(PageRequest.of(0, limit(limit)));
        for (var changeSet : changeSets) {
            inspected++;
            var act = actRepository.findById(changeSet.actId()).orElse(null);
            if (act == null) {
                skippedMissingActs++;
                continue;
            }
            var existing = changeDigestRepository.findByChangeSetId(changeSet.id()).orElse(null);
            if (existing != null && existing.source() == LegalChangeDigestSource.AI) {
                skippedAi++;
                continue;
            }
            var diffs = articleDiffRepository.findByChangeSetIdOrderByIdAsc(changeSet.id());
            changeDigestService.ensureDeterministicDigest(changeSet, act, diffs, now);
            if (existing == null) {
                created++;
            } else {
                refreshed++;
            }
        }
        return new LegalDigestRefreshResponse(inspected, created, refreshed, skippedAi, skippedMissingActs);
    }

    public LegalDigestAiDraftResponse generateDigestAiDraft(UserPrincipal principal, Long digestId) {
        platformAdminService.requirePlatformAdmin(principal);
        var digest = changeDigestRepository.findById(digestId)
                .orElseThrow(() -> new NotFoundException("Legal change digest not found"));
        var requestId = UUID.randomUUID();
        var request = new ArchDoxWorkerRequest(
                requestId,
                ArchDoxWorkerRequestSource.UI,
                "Generate legal change digest AI draft",
                new ArchDoxWorkerRequestContext(principal.userId(), null, null, null, null, null, "ko-KR"),
                Instant.now());
        var action = new ArchDoxWorkerAction(
                ArchDoxWorkerActionType.ENRICH_LEGAL_CHANGE_DIGEST,
                Map.of(
                        "digestId", digest.id(),
                        "changeSetId", digest.changeSetId(),
                        "dryRun", true),
                "Generate a source-backed AI draft for platform admin review.",
                1.0d,
                ArchDoxWorkerActionOrigin.USER);
        var handle = workerFlowFactory.createHandle(request, action);
        var timeout = Duration.ofSeconds(legalDigestAiProperties.safeTimeoutSeconds() + 5);
        if (!workerServiceWorker.submitAndAwait(handle.flow(), timeout)) {
            throw new BadRequestException(
                    "LEGAL_DIGEST_AI_DRAFT_TIMEOUT",
                    "errors.legal.digestAiDraftTimeout",
                    "Legal digest AI draft worker timed out.");
        }
        var result = handle.result();
        if (result == null) {
            throw new BadRequestException(
                    "LEGAL_DIGEST_AI_DRAFT_RESULT_MISSING",
                    "errors.legal.digestAiDraftResultMissing",
                    "Legal digest AI draft worker did not return a result.");
        }
        if (result.status() != ArchDoxWorkerActionExecutionStatus.SUCCEEDED) {
            throw new BadRequestException(
                    result.resultCode().isBlank() ? "LEGAL_DIGEST_AI_DRAFT_FAILED" : result.resultCode(),
                    "errors.legal.digestAiDraftFailed",
                    result.message().isBlank() ? "Legal digest AI draft worker failed." : result.message());
        }
        return toAiDraftResponse(result.output(), result.status().name(), result.resultCode(), requestId);
    }

    private LegalSyncRunResponse toRunResponse(com.archdox.cloud.legal.domain.LegalSyncRun run) {
        return new LegalSyncRunResponse(
                run.id(),
                run.triggerType(),
                run.sourceCode(),
                run.status(),
                run.startedAt(),
                run.completedAt(),
                run.failureCode(),
                run.summaryJson());
    }

    private int limit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }

    private void requireOpenDataSyncReady() {
        var openApi = legalSyncProperties.getOpenApi();
        var ocConfigured = openApi.getOc() != null && !openApi.getOc().isBlank();
        var activeTargets = activeTargets(openApi);
        if (openApi.isEnabled() && ocConfigured && !activeTargets.isEmpty()) {
            return;
        }
        throw new BadRequestException(
                "LEGAL_OPEN_API_NOT_READY",
                "errors.legal.openApiNotReady",
                "Legal Open API sync is not ready. Enable it, configure OC, and keep at least one target.",
                Map.of(
                        "enabled", openApi.isEnabled(),
                        "ocConfigured", ocConfigured,
                        "targetCount", activeTargets.size()));
    }

    private List<LegalSyncProperties.Target> activeTargets(LegalSyncProperties.OpenApi openApi) {
        return openApi.getTargets().stream()
                .filter(target -> target.getQuery() != null && !target.getQuery().isBlank())
                .toList();
    }

    private LegalDigestAiDraftResponse toAiDraftResponse(
            Map<String, Object> output,
            String workerStatus,
            String resultCode,
            UUID fallbackWorkerRequestId
    ) {
        return new LegalDigestAiDraftResponse(
                uuidValue(output.get("workerRequestId"), fallbackWorkerRequestId),
                longValue(output.get("digestId")),
                longValue(output.get("changeSetId")),
                booleanValue(output.get("dryRun")),
                workerStatus,
                resultCode,
                text(output.get("aiHarnessRunId")),
                text(output.get("digestDraftStatus")),
                text(output.get("title")),
                text(output.get("summary")),
                text(output.get("impactSummary")),
                text(output.get("confidence")),
                stringList(output.get("affectedReportTypes")),
                stringList(output.get("affectedCatalogItems")),
                stringList(output.get("keyArticles")),
                text(output.get("reviewNotes")),
                booleanValue(output.get("publicationApplied")),
                booleanValue(output.get("corpusMutated")),
                booleanValue(output.get("digestMutated")));
    }

    private UUID uuidValue(Object value, UUID fallback) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return UUID.fromString(text.trim());
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return false;
    }

    private List<String> stringList(Object value) {
        if (value instanceof Iterable<?> iterable) {
            var values = new ArrayList<String>();
            for (var item : iterable) {
                var text = text(item);
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
            return values;
        }
        return List.of();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

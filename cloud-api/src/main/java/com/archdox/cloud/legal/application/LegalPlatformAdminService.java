package com.archdox.cloud.legal.application;

import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.domain.LegalChangeDigest;
import com.archdox.cloud.legal.domain.LegalChangeDigestSource;
import com.archdox.cloud.legal.domain.LegalDigestAiDraft;
import com.archdox.cloud.legal.domain.LegalDigestAiDraftStatus;
import com.archdox.cloud.legal.domain.LegalSyncRun;
import com.archdox.cloud.legal.domain.LegalSyncRunStatus;
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
import com.archdox.cloud.legal.infra.LegalDigestAiDraftRepository;
import com.archdox.cloud.legal.infra.LegalSyncRunRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
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
import java.util.concurrent.CompletableFuture;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AiHarnessPolicyExecutionService aiHarnessPolicyExecutionService;
    private final LegalDigestAiDraftRepository aiDraftRepository;
    private final OperationEventService operationEventService;

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
            AiHarnessPolicyExecutionService aiHarnessPolicyExecutionService,
            LegalDigestAiDraftRepository aiDraftRepository,
            OperationEventService operationEventService
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
        this.aiHarnessPolicyExecutionService = aiHarnessPolicyExecutionService;
        this.aiDraftRepository = aiDraftRepository;
        this.operationEventService = operationEventService;
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
        return startSyncRun(FakeLegalSourceClient.DEFAULT_SOURCE_CODE, principal.userId());
    }

    public LegalSyncRunResponse startOpenDataSync(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        requireOpenDataSyncReady();
        return startSyncRun(LawOpenDataLegalSourceClient.SOURCE_CODE, principal.userId());
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

    public CompletableFuture<LegalDigestAiDraftResponse> generateDigestAiDraft(UserPrincipal principal, Long digestId) {
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
        var timeout = legalDigestAiDraftTimeout();
        return workerServiceWorker.submitAndTrackAsync(handle.flow(), timeout)
                .thenApply(awaited -> {
                    if (!awaited) {
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
                    var now = OffsetDateTime.now();
                    var draft = toAiDraft(
                            result.output(),
                            result.status().name(),
                            result.resultCode(),
                            requestId,
                            digest,
                            principal.userId(),
                            now);
                    requireSafeDraftOutput(draft);
                    var saved = aiDraftRepository.save(draft);
                    recordAiDraftEvent(
                            "LEGAL_DIGEST_AI_DRAFT_GENERATED",
                            "Legal digest AI draft was generated for platform admin review.",
                            digest,
                            saved,
                            principal);
                    return toAiDraftResponse(saved);
                });
    }

    private Duration legalDigestAiDraftTimeout() {
        var policy = aiHarnessPolicyExecutionService.resolve(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT);
        if (policy.runnable()) {
            return policy.plan().timeout().plusSeconds(5);
        }
        return Duration.ofSeconds(15);
    }

    public List<LegalDigestAiDraftResponse> digestAiDrafts(UserPrincipal principal, Long digestId) {
        platformAdminService.requirePlatformAdmin(principal);
        requireDigest(digestId);
        return aiDraftRepository.findByDigestIdOrderByGeneratedAtDescIdDesc(digestId)
                .stream()
                .map(this::toAiDraftResponse)
                .toList();
    }

    @Transactional
    public LegalDigestAiDraftResponse approveDigestAiDraft(UserPrincipal principal, Long digestId, Long draftId) {
        platformAdminService.requirePlatformAdmin(principal);
        var digest = requireDigest(digestId);
        var draft = requireDraftForDigest(digest, draftId);
        if (draft.status() != LegalDigestAiDraftStatus.NEEDS_HUMAN_REVIEW
                && draft.status() != LegalDigestAiDraftStatus.GENERATED) {
            throw new BadRequestException(
                    "LEGAL_DIGEST_AI_DRAFT_ALREADY_DECIDED",
                    "errors.legal.digestAiDraftAlreadyDecided",
                    "Legal digest AI draft was already approved, rejected, or applied.");
        }
        requireSafeDraftOutput(draft);
        var now = OffsetDateTime.now();
        draft.approve(principal.userId(), now);
        recordAiDraftEvent(
                "LEGAL_DIGEST_AI_DRAFT_APPROVED",
                "Legal digest AI draft was approved by a platform admin.",
                digest,
                draft,
                principal);
        return toAiDraftResponse(draft);
    }

    @Transactional
    public LegalDigestAiDraftResponse rejectDigestAiDraft(UserPrincipal principal, Long digestId, Long draftId) {
        platformAdminService.requirePlatformAdmin(principal);
        var digest = requireDigest(digestId);
        var draft = requireDraftForDigest(digest, draftId);
        if (draft.status() != LegalDigestAiDraftStatus.NEEDS_HUMAN_REVIEW
                && draft.status() != LegalDigestAiDraftStatus.GENERATED) {
            throw new BadRequestException(
                    "LEGAL_DIGEST_AI_DRAFT_ALREADY_DECIDED",
                    "errors.legal.digestAiDraftAlreadyDecided",
                    "Legal digest AI draft was already approved, rejected, or applied.");
        }
        var now = OffsetDateTime.now();
        draft.reject(principal.userId(), now);
        recordAiDraftEvent(
                "LEGAL_DIGEST_AI_DRAFT_REJECTED",
                "Legal digest AI draft was rejected by a platform admin.",
                digest,
                draft,
                principal);
        return toAiDraftResponse(draft);
    }

    @Transactional
    public LegalDigestAiDraftResponse applyDigestAiDraft(UserPrincipal principal, Long digestId, Long draftId) {
        platformAdminService.requirePlatformAdmin(principal);
        var digest = requireDigest(digestId);
        var draft = requireDraftForDigest(digest, draftId);
        if (draft.status() != LegalDigestAiDraftStatus.APPROVED) {
            throw new BadRequestException(
                    "LEGAL_DIGEST_AI_DRAFT_NOT_APPROVED",
                    "errors.legal.digestAiDraftNotApproved",
                    "Legal digest AI draft must be approved before it can be applied.");
        }
        requireSafeDraftOutput(draft);
        var now = OffsetDateTime.now();
        digest.applyAiDraft(
                draft.title(),
                draft.summary(),
                draft.impactSummary(),
                draft.affectedReportTypes(),
                draft.affectedCatalogItems(),
                draft.aiHarnessRunId(),
                now);
        draft.apply(principal.userId(), now);
        recordAiDraftEvent(
                "LEGAL_DIGEST_AI_DRAFT_APPLIED",
                "Legal digest AI draft was applied to the published digest.",
                digest,
                draft,
                principal);
        return toAiDraftResponse(draft);
    }

    private LegalChangeDigest requireDigest(Long digestId) {
        return changeDigestRepository.findById(digestId)
                .orElseThrow(() -> new NotFoundException("Legal change digest not found"));
    }

    private LegalDigestAiDraft requireDraftForDigest(LegalChangeDigest digest, Long draftId) {
        var draft = aiDraftRepository.findById(draftId)
                .orElseThrow(() -> new NotFoundException("Legal digest AI draft not found"));
        if (!digest.id().equals(draft.digestId())) {
            throw new BadRequestException(
                    "LEGAL_DIGEST_AI_DRAFT_DIGEST_MISMATCH",
                    "errors.legal.digestAiDraftDigestMismatch",
                    "Legal digest AI draft does not belong to this digest.");
        }
        return draft;
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

    private LegalSyncRunResponse startSyncRun(String sourceCode, Long userId) {
        var running = runningRun(sourceCode);
        if (running != null) {
            return toRunResponse(running);
        }
        try {
            var run = syncService.createRun("PLATFORM_ADMIN_MANUAL", sourceCode, userId);
            worker.submit(flowFactory.create(new LegalSyncRequested(run.id(), run.sourceCode())));
            return toRunResponse(run);
        } catch (DataIntegrityViolationException ex) {
            var raced = runningRun(sourceCode);
            if (raced != null) {
                return toRunResponse(raced);
            }
            throw ex;
        }
    }

    private LegalSyncRun runningRun(String sourceCode) {
        return syncRunRepository
                .findFirstBySourceCodeAndStatusOrderByStartedAtDescIdDesc(sourceCode, LegalSyncRunStatus.RUNNING)
                .orElse(null);
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

    private LegalDigestAiDraft toAiDraft(
            Map<String, Object> output,
            String workerStatus,
            String resultCode,
            UUID fallbackWorkerRequestId,
            LegalChangeDigest digest,
            Long generatedByUserId,
            OffsetDateTime now
    ) {
        var outputDigestId = longValue(output.get("digestId"));
        var outputChangeSetId = longValue(output.get("changeSetId"));
        if (outputDigestId != null && !outputDigestId.equals(digest.id())) {
            throw new BadRequestException(
                    "LEGAL_DIGEST_AI_DRAFT_OUTPUT_DIGEST_MISMATCH",
                    "errors.legal.digestAiDraftOutputDigestMismatch",
                    "Legal digest AI draft worker returned a mismatched digestId.");
        }
        if (outputChangeSetId != null && !outputChangeSetId.equals(digest.changeSetId())) {
            throw new BadRequestException(
                    "LEGAL_DIGEST_AI_DRAFT_OUTPUT_CHANGE_SET_MISMATCH",
                    "errors.legal.digestAiDraftOutputChangeSetMismatch",
                    "Legal digest AI draft worker returned a mismatched changeSetId.");
        }
        return new LegalDigestAiDraft(
                digest.id(),
                digest.changeSetId(),
                uuidValue(output.get("workerRequestId"), fallbackWorkerRequestId),
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
                booleanValue(output.get("digestMutated")),
                generatedByUserId,
                now);
    }

    private void requireSafeDraftOutput(LegalDigestAiDraft draft) {
        if (!draft.publicationApplied() && !draft.corpusMutated() && !draft.digestMutated()) {
            return;
        }
        throw new BadRequestException(
                "LEGAL_DIGEST_AI_DRAFT_UNSAFE_OUTPUT",
                "errors.legal.digestAiDraftUnsafeOutput",
                "Legal digest AI draft output attempted to mutate publication, corpus, or digest state.");
    }

    private LegalDigestAiDraftResponse toAiDraftResponse(LegalDigestAiDraft draft) {
        return new LegalDigestAiDraftResponse(
                draft.id(),
                draft.status(),
                draft.workerRequestId(),
                draft.digestId(),
                draft.changeSetId(),
                true,
                draft.workerStatus(),
                draft.resultCode(),
                draft.aiHarnessRunId(),
                draft.digestDraftStatus(),
                draft.title(),
                draft.summary(),
                draft.impactSummary(),
                draft.confidence(),
                draft.affectedReportTypes(),
                draft.affectedCatalogItems(),
                draft.keyArticles(),
                draft.reviewNotes(),
                draft.publicationApplied(),
                draft.corpusMutated(),
                draft.digestMutated(),
                draft.generatedByUserId(),
                draft.generatedAt(),
                draft.reviewedByUserId(),
                draft.reviewedAt(),
                draft.appliedByUserId(),
                draft.appliedAt());
    }

    private void recordAiDraftEvent(
            String eventType,
            String message,
            LegalChangeDigest digest,
            LegalDigestAiDraft draft,
            UserPrincipal principal
    ) {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("digestId", digest.id());
        payload.put("changeSetId", digest.changeSetId());
        payload.put("draftId", draft.id());
        payload.put("draftStatus", draft.status().name());
        payload.put("workerRequestId", draft.workerRequestId().toString());
        if (draft.aiHarnessRunId() != null) {
            payload.put("aiHarnessRunId", draft.aiHarnessRunId());
        }
        payload.put("source", digest.source().name());
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                eventType,
                "legal-digest-ai-draft",
                "digest:" + digest.id(),
                "LEGAL_DIGEST_AI_DRAFT",
                draft.id(),
                principal.userId(),
                draft.workerRequestId().toString(),
                message,
                payload);
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

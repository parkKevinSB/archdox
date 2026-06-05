package com.archdox.cloud.legal.application;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.dto.LegalChangeSetResponse;
import com.archdox.cloud.legal.dto.LegalChangeDigestResponse;
import com.archdox.cloud.legal.dto.LegalOpenApiStatusResponse;
import com.archdox.cloud.legal.dto.LegalSyncRunResponse;
import com.archdox.cloud.legal.event.LegalSyncRequested;
import com.archdox.cloud.legal.flow.LegalSyncFlowFactory;
import com.archdox.cloud.legal.flow.LegalSyncWorker;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import com.archdox.cloud.legal.infra.LegalChangeSetRepository;
import com.archdox.cloud.legal.infra.LegalSyncRunRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.util.List;
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
    private final LegalChangeSetRepository changeSetRepository;
    private final LegalChangeDigestRepository changeDigestRepository;
    private final LegalUpdateReadService updateReadService;
    private final LegalSyncProperties legalSyncProperties;

    public LegalPlatformAdminService(
            PlatformAdminService platformAdminService,
            LegalCorpusSyncService syncService,
            LegalSyncFlowFactory flowFactory,
            LegalSyncWorker worker,
            LegalSyncRunRepository syncRunRepository,
            LegalChangeSetRepository changeSetRepository,
            LegalChangeDigestRepository changeDigestRepository,
            LegalUpdateReadService updateReadService,
            LegalSyncProperties legalSyncProperties
    ) {
        this.platformAdminService = platformAdminService;
        this.syncService = syncService;
        this.flowFactory = flowFactory;
        this.worker = worker;
        this.syncRunRepository = syncRunRepository;
        this.changeSetRepository = changeSetRepository;
        this.changeDigestRepository = changeDigestRepository;
        this.updateReadService = updateReadService;
        this.legalSyncProperties = legalSyncProperties;
    }

    public LegalOpenApiStatusResponse openApiStatus(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        var openApi = legalSyncProperties.getOpenApi();
        return new LegalOpenApiStatusResponse(
                openApi.isEnabled(),
                openApi.getOc() != null && !openApi.getOc().isBlank(),
                openApi.getSourceCode(),
                openApi.getBaseUrl(),
                openApi.getUserAgent(),
                openApi.getRequestTimeoutMs(),
                openApi.getRequestIntervalMs(),
                openApi.getMaxAttempts(),
                openApi.getTargets().stream()
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
        return changeDigestRepository.findAllByOrderByDetectedAtDescIdDesc(PageRequest.of(0, limit(limit)))
                .stream()
                .map(updateReadService::toResponse)
                .toList();
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
}

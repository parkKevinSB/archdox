package com.archdox.cloud.aiharness.application;

import com.archdox.cloud.aiharness.domain.AiWorkerEvaluationRun;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationRunResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSignalResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSummaryResponse;
import com.archdox.cloud.aiharness.infra.AiWorkerEvaluationRunRepository;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiWorkerEvaluationRunService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final String TRIGGER_PLATFORM_ADMIN_SNAPSHOT = "PLATFORM_ADMIN_SNAPSHOT";
    private static final String TRIGGER_PLATFORM_ADMIN_RUNTIME_PROBE = "PLATFORM_ADMIN_RUNTIME_PROBE";
    private static final String STATUS_PASS = "PASS";
    private static final String STATUS_WARN = "WARN";
    private static final String STATUS_FAILED = "FAILED";

    private final AiWorkerEvaluationRunRepository repository;
    private final AiWorkerEvaluationReadService readService;
    private final AiWorkerEvaluationRuntimeProbeService runtimeProbeService;
    private final PlatformAdminService platformAdminService;
    private final ObjectMapper objectMapper;

    public AiWorkerEvaluationRunService(
            AiWorkerEvaluationRunRepository repository,
            AiWorkerEvaluationReadService readService,
            AiWorkerEvaluationRuntimeProbeService runtimeProbeService,
            PlatformAdminService platformAdminService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.readService = readService;
        this.runtimeProbeService = runtimeProbeService;
        this.platformAdminService = platformAdminService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiWorkerEvaluationRunResponse createSnapshot(UserPrincipal principal) {
        var summary = readService.summary(principal);
        return saveRun(principal, summary, TRIGGER_PLATFORM_ADMIN_SNAPSHOT);
    }

    public AiWorkerEvaluationRunResponse createRuntimeProbe(UserPrincipal principal) {
        var summary = runtimeProbeService.runtimeProbe(principal);
        return saveRun(principal, summary, TRIGGER_PLATFORM_ADMIN_RUNTIME_PROBE);
    }

    private AiWorkerEvaluationRunResponse saveRun(
            UserPrincipal principal,
            AiWorkerEvaluationSummaryResponse summary,
            String triggerType
    ) {
        var now = OffsetDateTime.now();
        var warningSignals = countSignals(summary, STATUS_WARN);
        var failedSignals = countSignals(summary, STATUS_FAILED);
        var run = new AiWorkerEvaluationRun(
                "aiw_eval_" + UUID.randomUUID(),
                triggerType,
                status(summary, warningSignals, failedSignals),
                summary.evaluationMode(),
                summary.totalCases(),
                summary.automatedCases(),
                summary.passedCases(),
                summary.warningCases(),
                summary.failedCases(),
                summary.passRatePercent(),
                summary.groups().size(),
                summary.signals().size(),
                warningSignals,
                failedSignals,
                snapshot(summary),
                principal.userId(),
                principal.email(),
                now);
        return toResponse(repository.save(run));
    }

    @Transactional(readOnly = true)
    public List<AiWorkerEvaluationRunResponse> runs(UserPrincipal principal, Integer limit) {
        platformAdminService.requirePlatformAdmin(principal);
        return repository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, boundedLimit(limit)))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiWorkerEvaluationRunResponse run(UserPrincipal principal, Long runId) {
        platformAdminService.requirePlatformAdmin(principal);
        return repository.findById(runId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("AI worker evaluation run not found"));
    }

    private AiWorkerEvaluationRunResponse toResponse(AiWorkerEvaluationRun run) {
        return new AiWorkerEvaluationRunResponse(
                run.id(),
                run.runKey(),
                run.triggerType(),
                run.status(),
                run.evaluationMode(),
                run.totalCases(),
                run.automatedCases(),
                run.passedCases(),
                run.warningCases(),
                run.failedCases(),
                run.passRatePercent(),
                run.groupCount(),
                run.signalCount(),
                run.warningSignalCount(),
                run.failedSignalCount(),
                run.triggeredByUserId(),
                run.triggeredByEmail(),
                run.createdAt(),
                run.completedAt(),
                objectMapper.convertValue(run.summaryJson(), AiWorkerEvaluationSummaryResponse.class));
    }

    private Map<String, Object> snapshot(AiWorkerEvaluationSummaryResponse summary) {
        return objectMapper.convertValue(summary, new TypeReference<>() {
        });
    }

    private String status(AiWorkerEvaluationSummaryResponse summary, int warningSignals, int failedSignals) {
        if (summary.failedCases() > 0 || failedSignals > 0) {
            return STATUS_FAILED;
        }
        if (summary.warningCases() > 0 || warningSignals > 0) {
            return STATUS_WARN;
        }
        return STATUS_PASS;
    }

    private int countSignals(AiWorkerEvaluationSummaryResponse summary, String status) {
        return (int) summary.signals().stream()
                .map(AiWorkerEvaluationSignalResponse::status)
                .filter(status::equals)
                .count();
    }

    private int boundedLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }
}

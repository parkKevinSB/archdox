package com.archdox.cloud.aiharness.dto;

import java.time.OffsetDateTime;

public record AiWorkerEvaluationRunResponse(
        Long id,
        String runKey,
        String triggerType,
        String status,
        String evaluationMode,
        int totalCases,
        int automatedCases,
        int passedCases,
        int warningCases,
        int failedCases,
        int passRatePercent,
        int groupCount,
        int signalCount,
        int warningSignalCount,
        int failedSignalCount,
        Long triggeredByUserId,
        String triggeredByEmail,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        AiWorkerEvaluationSummaryResponse summary
) {
}

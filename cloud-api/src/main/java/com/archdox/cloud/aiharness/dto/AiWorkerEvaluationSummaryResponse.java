package com.archdox.cloud.aiharness.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AiWorkerEvaluationSummaryResponse(
        OffsetDateTime generatedAt,
        String evaluationMode,
        String dataPolicy,
        int totalCases,
        int automatedCases,
        int passedCases,
        int warningCases,
        int failedCases,
        int passRatePercent,
        List<AiWorkerEvaluationGroupResponse> groups,
        List<AiWorkerEvaluationSignalResponse> signals
) {
}

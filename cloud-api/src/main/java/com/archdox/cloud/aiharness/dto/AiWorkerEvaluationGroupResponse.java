package com.archdox.cloud.aiharness.dto;

import java.util.List;

public record AiWorkerEvaluationGroupResponse(
        String groupKey,
        String displayName,
        String layer,
        int totalCases,
        int automatedCases,
        int passedCases,
        int warningCases,
        int failedCases,
        int passRatePercent,
        List<AiWorkerEvaluationCaseResponse> cases
) {
}

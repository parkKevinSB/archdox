package com.archdox.cloud.aiharness.dto;

public record AiWorkerEvaluationSignalResponse(
        String signalKey,
        String displayName,
        String status,
        String layer,
        String evidence
) {
}

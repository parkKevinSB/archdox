package com.archdox.cloud.aiharness.dto;

public record AiWorkerEvaluationCaseResponse(
        String caseId,
        String name,
        String layer,
        String status,
        boolean automated,
        String verification,
        String evidence
) {
}

package com.archdox.cloud.flower.dto;

public record FlowerExecutionContextResponse(
        String tenantId,
        String userId,
        String sessionId,
        String runId,
        String traceId,
        String correlationId
) {
}

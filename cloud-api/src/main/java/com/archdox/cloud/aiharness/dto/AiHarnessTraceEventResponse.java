package com.archdox.cloud.aiharness.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record AiHarnessTraceEventResponse(
        Long id,
        Long officeId,
        String harnessRunId,
        String harnessId,
        String eventType,
        String status,
        Integer attempt,
        String modelId,
        String callId,
        String promptId,
        String promptVersion,
        Integer inputTokens,
        Integer outputTokens,
        Long latencyMs,
        Integer findingCount,
        Boolean validationValid,
        Integer validationErrorCount,
        String errorType,
        String message,
        Map<String, Object> attributes,
        OffsetDateTime createdAt
) {
}

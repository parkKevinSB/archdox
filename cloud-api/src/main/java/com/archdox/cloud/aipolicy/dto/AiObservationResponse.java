package com.archdox.cloud.aipolicy.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AiObservationResponse(
        String callId,
        String status,
        String providerCode,
        String modelId,
        String modelName,
        Long officeId,
        String feature,
        String workflowType,
        String workflowKey,
        String resourceType,
        String resourceId,
        Map<String, Object> requestOptions,
        List<AiObservationMessageResponse> promptMessages,
        boolean promptTruncated,
        String responseText,
        boolean responseTruncated,
        Integer inputTokens,
        Integer outputTokens,
        Long latencyMs,
        String finishReason,
        Map<String, String> providerTrace,
        String errorType,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

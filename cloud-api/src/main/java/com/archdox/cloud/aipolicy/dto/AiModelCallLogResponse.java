package com.archdox.cloud.aipolicy.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AiModelCallLogResponse(
        Long id,
        String callId,
        Long officeId,
        Long userId,
        Long providerCredentialId,
        String providerCode,
        String providerType,
        String modelId,
        String modelName,
        String feature,
        String workflowType,
        String workflowKey,
        String resourceType,
        String resourceId,
        String status,
        Integer inputTokens,
        Integer outputTokens,
        Long latencyMs,
        String finishReason,
        String providerResponseId,
        String errorType,
        String errorMessage,
        Long pricingRuleId,
        String costCurrency,
        BigDecimal estimatedInputCost,
        BigDecimal estimatedOutputCost,
        BigDecimal estimatedTotalCost,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt
) {
}

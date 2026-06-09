package com.archdox.cloud.aipolicy.dto;

public record AiPricingCoverageResponse(
        String sourceType,
        String sourceKey,
        String providerCode,
        String modelName,
        boolean configured,
        String matchedBy,
        Long pricingRuleId,
        String status,
        String message
) {
}

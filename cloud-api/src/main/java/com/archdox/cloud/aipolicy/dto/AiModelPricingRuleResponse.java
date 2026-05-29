package com.archdox.cloud.aipolicy.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AiModelPricingRuleResponse(
        Long id,
        String providerCode,
        String modelName,
        String currency,
        BigDecimal inputTokenPricePerMillion,
        BigDecimal outputTokenPricePerMillion,
        String status,
        Long createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime disabledAt
) {
}

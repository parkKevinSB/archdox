package com.archdox.cloud.aipolicy.dto;

import java.math.BigDecimal;

public record CreateAiModelPricingRuleRequest(
        String providerCode,
        String modelName,
        String currency,
        BigDecimal inputTokenPricePerMillion,
        BigDecimal outputTokenPricePerMillion
) {
}

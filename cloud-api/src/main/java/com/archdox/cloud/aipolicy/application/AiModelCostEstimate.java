package com.archdox.cloud.aipolicy.application;

import java.math.BigDecimal;

public record AiModelCostEstimate(
        Long pricingRuleId,
        String currency,
        BigDecimal inputCost,
        BigDecimal outputCost,
        BigDecimal totalCost
) {
}

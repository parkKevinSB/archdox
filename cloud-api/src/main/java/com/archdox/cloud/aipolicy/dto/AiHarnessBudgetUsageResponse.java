package com.archdox.cloud.aipolicy.dto;

import java.math.BigDecimal;

public record AiHarnessBudgetUsageResponse(
        String policyKey,
        String displayName,
        boolean effectiveEnabled,
        String providerCode,
        String modelName,
        Integer maxOutputTokens,
        boolean budgetEnforcementEnabled,
        Integer dailyCallLimit,
        long dailyCallCount,
        Long monthlyTokenLimit,
        long monthlyTokens,
        BigDecimal monthlyBudgetAmount,
        String budgetCurrency,
        BigDecimal monthlyEstimatedCost,
        boolean pricingRuleConfigured,
        String status,
        String message
) {
}

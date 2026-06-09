package com.archdox.cloud.aipolicy.dto;

import java.math.BigDecimal;

public record UpdateAiHarnessPolicyRequest(
        Boolean enabled,
        Long providerCredentialId,
        String modelName,
        Integer maxAttempts,
        Long timeoutSeconds,
        Integer maxOutputTokens,
        Boolean budgetEnforcementEnabled,
        BigDecimal monthlyBudgetAmount,
        String budgetCurrency,
        Integer dailyCallLimit,
        Long monthlyTokenLimit
) {
}

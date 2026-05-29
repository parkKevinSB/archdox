package com.archdox.cloud.aipolicy.dto;

import java.math.BigDecimal;

public record UpdateOfficeAiPolicyRequest(
        Boolean aiEnabled,
        Boolean documentReviewAiEnabled,
        Boolean documentGenerationAiEnabled,
        Long preferredProviderCredentialId,
        String credentialDeliveryMode,
        Boolean budgetEnforcementEnabled,
        BigDecimal monthlyBudgetAmount,
        String budgetCurrency,
        Integer dailyCallLimit,
        Long monthlyTokenLimit
) {
}

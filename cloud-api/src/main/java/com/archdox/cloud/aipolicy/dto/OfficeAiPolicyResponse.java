package com.archdox.cloud.aipolicy.dto;

import com.archdox.cloud.aipolicy.domain.AiCredentialDeliveryMode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OfficeAiPolicyResponse(
        Long officeId,
        String officeCode,
        String officeName,
        boolean aiEnabled,
        boolean documentReviewAiEnabled,
        boolean documentGenerationAiEnabled,
        Long preferredProviderCredentialId,
        String preferredProviderCode,
        String preferredProviderType,
        AiCredentialDeliveryMode credentialDeliveryMode,
        boolean budgetEnforcementEnabled,
        BigDecimal monthlyBudgetAmount,
        String budgetCurrency,
        Integer dailyCallLimit,
        Long monthlyTokenLimit,
        long policyVersion,
        boolean effectiveAiEnabled,
        String effectiveMessage,
        OffsetDateTime updatedAt
) {
}

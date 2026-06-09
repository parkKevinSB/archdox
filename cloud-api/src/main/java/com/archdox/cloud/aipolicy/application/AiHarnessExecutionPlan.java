package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import java.math.BigDecimal;
import java.time.Duration;

public record AiHarnessExecutionPlan(
        AiHarnessPolicyKey policyKey,
        AiProviderCredential provider,
        ModelId modelId,
        int maxAttempts,
        Duration timeout,
        int maxOutputTokens,
        boolean budgetEnforcementEnabled,
        int dailyCallLimit,
        long monthlyTokenLimit,
        BigDecimal monthlyBudgetAmount,
        String budgetCurrency
) {
    public AiHarnessExecutionPlan(
            AiHarnessPolicyKey policyKey,
            AiProviderCredential provider,
            ModelId modelId,
            int maxAttempts,
            Duration timeout
    ) {
        this(policyKey, provider, modelId, maxAttempts, timeout, 1_200, true, 30, 500_000L, null, "USD");
    }
}

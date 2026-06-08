package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;

public record AiHarnessPolicyResolution(
        AiHarnessPolicyKey policyKey,
        AiHarnessExecutionPlan plan,
        String unavailableReason
) {
    public static AiHarnessPolicyResolution runnable(AiHarnessExecutionPlan plan) {
        return new AiHarnessPolicyResolution(plan.policyKey(), plan, null);
    }

    public static AiHarnessPolicyResolution unavailable(AiHarnessPolicyKey policyKey, String reason) {
        return new AiHarnessPolicyResolution(policyKey, null, reason);
    }

    public boolean runnable() {
        return plan != null;
    }
}

package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import java.time.Duration;

public record AiHarnessExecutionPlan(
        AiHarnessPolicyKey policyKey,
        AiProviderCredential provider,
        ModelId modelId,
        int maxAttempts,
        Duration timeout
) {
}

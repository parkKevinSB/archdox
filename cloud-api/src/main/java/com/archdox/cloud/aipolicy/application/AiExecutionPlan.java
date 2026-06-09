package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;

public record AiExecutionPlan(
        Long officeId,
        Long userId,
        AiFeature feature,
        AiProviderCredential provider,
        ModelId modelId,
        Integer maxOutputTokens
) {
    public AiExecutionPlan(
            Long officeId,
            AiFeature feature,
            AiProviderCredential provider,
            ModelId modelId
    ) {
        this(officeId, null, feature, provider, modelId, null);
    }
}

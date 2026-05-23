package com.archdox.cloud.ai.application;

public interface AiTextGenerationService {
    AiProvider provider();

    default boolean supports(AiProvider provider) {
        return provider() == provider;
    }

    AiTextGenerationResponse generate(AiTextGenerationRequest request);
}

package com.archdox.cloud.ai.application;

public record AiTextGenerationResponse(
        String text,
        String model,
        AiProvider provider
) {
}

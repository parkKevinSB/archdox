package com.archdox.cloud.ai.application;

import java.util.Map;

public record AiTextGenerationRequest(
        String systemPrompt,
        String prompt,
        Map<String, Object> metadata
) {
}

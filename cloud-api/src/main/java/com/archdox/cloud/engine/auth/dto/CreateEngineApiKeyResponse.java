package com.archdox.cloud.engine.auth.dto;

public record CreateEngineApiKeyResponse(
        EngineApiKeyResponse key,
        String apiKey
) {
}

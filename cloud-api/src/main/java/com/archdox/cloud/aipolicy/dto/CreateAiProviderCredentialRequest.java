package com.archdox.cloud.aipolicy.dto;

public record CreateAiProviderCredentialRequest(
        String providerCode,
        String displayName,
        String providerType,
        String baseUrl,
        String defaultModel,
        String apiKey
) {
}

package com.archdox.cloud.aipolicy.dto;

public record UpdateAiProviderCredentialRequest(
        String displayName,
        String providerType,
        String baseUrl,
        String defaultModel,
        String apiKey
) {
}

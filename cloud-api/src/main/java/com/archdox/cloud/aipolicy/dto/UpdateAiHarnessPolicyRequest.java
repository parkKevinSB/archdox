package com.archdox.cloud.aipolicy.dto;

public record UpdateAiHarnessPolicyRequest(
        Boolean enabled,
        Long providerCredentialId,
        String modelName,
        Integer maxAttempts,
        Long timeoutSeconds
) {
}

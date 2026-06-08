package com.archdox.cloud.aipolicy.dto;

import java.time.OffsetDateTime;

public record AiHarnessPolicyResponse(
        Long id,
        String policyKey,
        String displayName,
        String description,
        boolean enabled,
        Long providerCredentialId,
        String providerCode,
        String providerDisplayName,
        String providerType,
        String modelName,
        String effectiveModelName,
        int maxAttempts,
        long timeoutSeconds,
        long policyVersion,
        boolean effectiveEnabled,
        String effectiveMessage,
        OffsetDateTime updatedAt
) {
}

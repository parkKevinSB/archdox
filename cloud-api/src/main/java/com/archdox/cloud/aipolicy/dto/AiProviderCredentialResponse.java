package com.archdox.cloud.aipolicy.dto;

import com.archdox.cloud.aipolicy.domain.AiProviderCredentialStatus;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import java.time.OffsetDateTime;

public record AiProviderCredentialResponse(
        Long id,
        String providerCode,
        String displayName,
        AiProviderType providerType,
        AiProviderCredentialStatus status,
        String baseUrl,
        String defaultModel,
        Long credentialVersion,
        String apiKeyFingerprint,
        String apiKeyMasked,
        boolean apiKeyConfigured,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime publishedAt
) {
}

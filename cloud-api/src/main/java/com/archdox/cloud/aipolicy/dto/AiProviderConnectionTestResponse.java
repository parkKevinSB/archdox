package com.archdox.cloud.aipolicy.dto;

import java.time.OffsetDateTime;

public record AiProviderConnectionTestResponse(
        Long providerId,
        String providerCode,
        String providerType,
        String modelName,
        boolean success,
        String status,
        String message,
        Long latencyMs,
        String finishReason,
        String responsePreview,
        OffsetDateTime testedAt
) {
}

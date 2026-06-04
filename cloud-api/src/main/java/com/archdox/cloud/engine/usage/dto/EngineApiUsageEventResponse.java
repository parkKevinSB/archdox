package com.archdox.cloud.engine.usage.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record EngineApiUsageEventResponse(
        Long id,
        Long apiKeyId,
        String keyId,
        Long ownerUserId,
        Long officeId,
        String capability,
        String operation,
        String reviewSessionId,
        String status,
        int requestUnits,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {
    public EngineApiUsageEventResponse {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

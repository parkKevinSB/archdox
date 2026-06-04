package com.archdox.cloud.engine.usage.dto;

import java.time.OffsetDateTime;

public record EngineApiUsageGroupResponse(
        Long apiKeyId,
        String keyId,
        Long ownerUserId,
        Long officeId,
        String capability,
        String operation,
        long eventCount,
        long requestUnits,
        OffsetDateTime lastCalledAt
) {
}

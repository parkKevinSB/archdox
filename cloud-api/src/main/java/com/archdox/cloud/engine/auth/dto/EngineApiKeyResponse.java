package com.archdox.cloud.engine.auth.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record EngineApiKeyResponse(
        Long id,
        String keyId,
        String maskedKey,
        String displayName,
        Long ownerUserId,
        Long officeId,
        Long issuedByUserId,
        List<String> scopes,
        Integer dailyRequestUnitLimit,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime revokedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

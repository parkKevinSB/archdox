package com.archdox.cloud.engine.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateEngineApiKeyRequest(
        @NotBlank String displayName,
        @NotNull Long ownerUserId,
        Long officeId,
        List<String> scopes,
        @Min(1) Integer dailyRequestUnitLimit,
        OffsetDateTime expiresAt
) {
    public CreateEngineApiKeyRequest {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}

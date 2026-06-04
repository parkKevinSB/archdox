package com.archdox.cloud.engine.connect.dto;

import com.archdox.cloud.engine.connect.domain.EngineConnectClientType;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record CreateEngineConnectBootstrapRequest(
        @NotNull EngineConnectClientType clientType,
        String displayName,
        Long officeId,
        OffsetDateTime expiresAt
) {
}

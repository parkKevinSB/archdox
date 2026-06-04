package com.archdox.cloud.engine.connect.dto;

import com.archdox.cloud.engine.connect.domain.EngineConnectClientType;

public record EngineConnectClientResponse(
        EngineConnectClientType type,
        String displayName,
        String description
) {
}

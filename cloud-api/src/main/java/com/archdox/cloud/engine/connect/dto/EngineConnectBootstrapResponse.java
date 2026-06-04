package com.archdox.cloud.engine.connect.dto;

import com.archdox.cloud.engine.auth.dto.EngineApiKeyResponse;
import com.archdox.cloud.engine.connect.domain.EngineConnectClientType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record EngineConnectBootstrapResponse(
        String connectionId,
        EngineConnectClientType clientType,
        String displayName,
        Long ownerUserId,
        Long officeId,
        EngineApiKeyResponse key,
        String apiKey,
        String engineApiBaseUrl,
        String mcpServerUrl,
        Map<String, String> headers,
        Map<String, Object> suggestedMcpConfig,
        String curlExample,
        List<String> nextSteps,
        OffsetDateTime createdAt
) {
}

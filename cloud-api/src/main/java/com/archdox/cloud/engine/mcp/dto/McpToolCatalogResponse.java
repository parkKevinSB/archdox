package com.archdox.cloud.engine.mcp.dto;

import java.util.List;
import java.util.Map;

public record McpToolCatalogResponse(
        String name,
        String title,
        String description,
        String capability,
        String requiredScope,
        String accessMode,
        String operation,
        String status,
        boolean gatewayManagedUsage,
        String usageMetering,
        int baseRequestUnits,
        String requestUnitPolicy,
        Map<String, Object> inputSchema,
        Map<String, Object> exampleArguments,
        List<String> errorCodes,
        String boundary
) {
}

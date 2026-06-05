package com.archdox.cloud.engine.mcp;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record McpExecutionContext(
        String method,
        Object jsonRpcId,
        String correlationId,
        String remoteIp,
        String userAgent
) {
    public static McpExecutionContext from(McpJsonRpcRequest request, HttpServletRequest httpRequest) {
        return new McpExecutionContext(
                request.method(),
                request.id(),
                correlationId(httpRequest),
                remoteIp(httpRequest),
                header(httpRequest, "User-Agent"));
    }

    public Map<String, Object> metadata(McpToolDefinition definition) {
        var metadata = new LinkedHashMap<String, Object>();
        put(metadata, "source", "MCP");
        put(metadata, "mcpMethod", method);
        put(metadata, "jsonRpcId", jsonRpcId == null ? null : String.valueOf(jsonRpcId));
        put(metadata, "correlationId", correlationId);
        put(metadata, "remoteIp", remoteIp);
        put(metadata, "userAgent", userAgent);
        if (definition != null) {
            put(metadata, "toolName", definition.name());
            put(metadata, "toolVersion", "v1");
            put(metadata, "accessMode", definition.accessMode());
            put(metadata, "requiredScope", definition.requiredScope());
            put(metadata, "capability", definition.capability());
        }
        return Map.copyOf(metadata);
    }

    private static String correlationId(HttpServletRequest request) {
        var value = header(request, "X-Correlation-Id");
        if (value == null) {
            value = header(request, "X-Request-Id");
        }
        return value == null ? "mcp_" + UUID.randomUUID() : value;
    }

    private static String remoteIp(HttpServletRequest request) {
        var forwardedFor = header(request, "X-Forwarded-For");
        if (forwardedFor != null) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String header(HttpServletRequest request, String name) {
        var value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void put(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}

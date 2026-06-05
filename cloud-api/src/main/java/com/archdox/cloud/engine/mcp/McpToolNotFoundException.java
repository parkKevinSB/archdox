package com.archdox.cloud.engine.mcp;

import com.archdox.cloud.global.api.ApiException;
import java.util.Map;

public class McpToolNotFoundException extends RuntimeException implements ApiException {
    private final String toolName;

    public McpToolNotFoundException(String toolName) {
        super("Unknown ArchDox MCP tool: " + toolName);
        this.toolName = toolName;
    }

    @Override
    public String code() {
        return "MCP_TOOL_NOT_FOUND";
    }

    @Override
    public String messageKey() {
        return "errors.mcp.toolNotFound";
    }

    @Override
    public Map<String, Object> params() {
        return Map.of("toolName", toolName);
    }
}

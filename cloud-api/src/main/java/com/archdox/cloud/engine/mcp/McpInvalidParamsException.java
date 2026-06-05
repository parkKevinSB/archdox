package com.archdox.cloud.engine.mcp;

import com.archdox.cloud.global.api.ApiException;
import java.util.Map;

public class McpInvalidParamsException extends RuntimeException implements ApiException {
    private final Map<String, Object> params;

    public McpInvalidParamsException(String message) {
        this(message, Map.of());
    }

    public McpInvalidParamsException(String message, Map<String, Object> params) {
        super(message);
        this.params = params == null ? Map.of() : Map.copyOf(params);
    }

    @Override
    public String code() {
        return "MCP_INVALID_PARAMS";
    }

    @Override
    public String messageKey() {
        return "errors.mcp.invalidParams";
    }

    @Override
    public Map<String, Object> params() {
        return params;
    }
}

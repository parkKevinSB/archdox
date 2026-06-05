package com.archdox.cloud.engine.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpJsonRpcRequest(
        String jsonrpc,
        Object id,
        String method,
        Map<String, Object> params
) {
    public McpJsonRpcRequest {
        jsonrpc = jsonrpc == null || jsonrpc.isBlank() ? "2.0" : jsonrpc.trim();
        method = method == null ? "" : method.trim();
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}

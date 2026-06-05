package com.archdox.cloud.engine.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

public final class McpJsonRpcResponse {
    private McpJsonRpcResponse() {
    }

    public static Map<String, Object> result(Object id, Object result) {
        var response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result == null ? Map.of() : result);
        return Map.copyOf(response);
    }

    public static Map<String, Object> error(Object id, int code, String message, Map<String, Object> data) {
        var error = new LinkedHashMap<String, Object>();
        error.put("code", code);
        error.put("message", message == null || message.isBlank() ? "MCP error" : message);
        if (data != null && !data.isEmpty()) {
            error.put("data", data);
        }

        var response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.copyOf(error));
        return Map.copyOf(response);
    }
}

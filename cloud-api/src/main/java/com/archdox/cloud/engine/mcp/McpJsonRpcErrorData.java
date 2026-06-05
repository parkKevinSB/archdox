package com.archdox.cloud.engine.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

public record McpJsonRpcErrorData(
        String code,
        McpJsonRpcErrorCategory category,
        boolean retryable,
        String messageKey,
        Map<String, Object> params
) {
    public McpJsonRpcErrorData {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public Map<String, Object> toMap() {
        var data = new LinkedHashMap<String, Object>();
        data.put("code", code);
        data.put("category", category.name());
        data.put("retryable", retryable);
        if (messageKey != null && !messageKey.isBlank()) {
            data.put("messageKey", messageKey);
        }
        data.put("params", params);
        return Map.copyOf(data);
    }
}

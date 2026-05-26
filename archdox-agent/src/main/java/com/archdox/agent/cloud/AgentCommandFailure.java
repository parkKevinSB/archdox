package com.archdox.agent.cloud;

import java.util.LinkedHashMap;
import java.util.Map;

public record AgentCommandFailure(
        String errorCode,
        boolean retryable,
        String message
) {
    public Map<String, Object> result() {
        var result = new LinkedHashMap<String, Object>();
        result.put("errorCode", errorCode);
        result.put("retryable", retryable);
        result.put("message", message);
        return result;
    }
}

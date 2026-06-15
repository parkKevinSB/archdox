package com.archdox.cloud.engine.mcp.dto;

public record McpLiveSmokeStepResponse(
        String step,
        String method,
        String toolName,
        int httpStatus,
        String status,
        boolean success,
        long elapsedMs,
        String summary,
        String errorCode,
        String errorCategory,
        Boolean retryable,
        String responsePreview
) {
}

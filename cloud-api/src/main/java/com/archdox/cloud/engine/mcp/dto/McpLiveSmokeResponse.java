package com.archdox.cloud.engine.mcp.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record McpLiveSmokeResponse(
        String endpoint,
        String status,
        boolean success,
        int stepCount,
        int succeededCount,
        int failedCount,
        long elapsedMs,
        List<McpLiveSmokeStepResponse> steps,
        OffsetDateTime createdAt
) {
}

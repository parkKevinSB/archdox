package com.archdox.cloud.engine.dto;

import java.time.OffsetDateTime;

public record EngineReviewResultResponse(
        String reviewSessionId,
        String status,
        boolean resultReady,
        EngineValidationResultResponse validationResult,
        OffsetDateTime updatedAt,
        OffsetDateTime normalizedAt,
        OffsetDateTime completedAt
) {
    public EngineReviewResultResponse {
        reviewSessionId = reviewSessionId == null ? "" : reviewSessionId.trim();
        status = status == null ? "" : status.trim();
        validationResult = validationResult == null ? EngineValidationResultResponse.empty() : validationResult;
    }
}

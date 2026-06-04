package com.archdox.cloud.engine.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record EngineReviewSessionResponse(
        String reviewSessionId,
        String status,
        String customerProjectRef,
        String reviewPurpose,
        String documentTypeHint,
        String fileName,
        List<Map<String, Object>> facts,
        Map<String, Object> normalizedContext,
        EngineValidationResultResponse validationResult,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime normalizedAt,
        OffsetDateTime completedAt
) {
    public EngineReviewSessionResponse {
        facts = facts == null ? List.of() : List.copyOf(facts);
        normalizedContext = normalizedContext == null ? Map.of() : Map.copyOf(normalizedContext);
        validationResult = validationResult == null ? EngineValidationResultResponse.empty() : validationResult;
    }
}

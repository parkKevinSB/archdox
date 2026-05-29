package com.archdox.cloud.documentai.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record DocumentAiReviewFindingResponse(
        Long id,
        String code,
        String severity,
        String location,
        String message,
        String evidence,
        Map<String, String> attributes,
        OffsetDateTime createdAt
) {
}

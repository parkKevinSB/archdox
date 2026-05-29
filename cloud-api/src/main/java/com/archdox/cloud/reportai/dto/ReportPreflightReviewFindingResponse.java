package com.archdox.cloud.reportai.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ReportPreflightReviewFindingResponse(
        Long id,
        String source,
        String code,
        String severity,
        String location,
        String message,
        String evidence,
        Map<String, String> attributes,
        String resolutionStatus,
        String resolutionNote,
        Long resolvedBy,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt
) {
}

package com.archdox.cloud.reportai.dto;

import java.time.OffsetDateTime;
import java.util.List;
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
        String engineRunId,
        String engineStatus,
        List<String> legalReferences,
        List<ReportPreflightLegalReferenceResponse> legalReferenceDetails,
        List<String> nextActions,
        String resolutionStatus,
        String resolutionNote,
        Long resolvedBy,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt
) {
}

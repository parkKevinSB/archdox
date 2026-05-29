package com.archdox.cloud.reportai.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record PlatformReportPreflightFindingResponse(
        Long id,
        Long officeId,
        Long reportId,
        Long reviewRunId,
        String reviewRunStatus,
        String reviewRunTerminalReason,
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

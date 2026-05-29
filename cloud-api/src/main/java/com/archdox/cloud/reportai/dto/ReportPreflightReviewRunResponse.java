package com.archdox.cloud.reportai.dto;

import java.time.OffsetDateTime;

public record ReportPreflightReviewRunResponse(
        Long id,
        Long officeId,
        Long reportId,
        int reportRevision,
        String status,
        Long requestedBy,
        String terminalReason,
        boolean aiReviewPlanned,
        String harnessRunId,
        String harnessStatus,
        int harnessAttempt,
        String harnessTerminalReason,
        String aiProviderCode,
        String aiModelId,
        OffsetDateTime requestedAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
}

package com.archdox.cloud.documentai.dto;

import java.time.OffsetDateTime;

public record DocumentAiReviewRunResponse(
        Long id,
        Long officeId,
        Long documentJobId,
        Long reportId,
        String harnessRunId,
        String harnessId,
        String promptVersion,
        String status,
        int attempt,
        String terminalReason,
        Long requestedBy,
        OffsetDateTime requestedAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
}

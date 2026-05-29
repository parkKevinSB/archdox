package com.archdox.cloud.reportai.flow;

public record ReportPreflightReviewRequest(
        Long officeId,
        Long reportId,
        Long reviewRunId,
        Long requestedBy
) {
}

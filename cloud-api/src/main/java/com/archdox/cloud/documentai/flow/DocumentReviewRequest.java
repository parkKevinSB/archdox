package com.archdox.cloud.documentai.flow;

public record DocumentReviewRequest(
        Long officeId,
        Long documentJobId,
        Long reportId,
        Long reviewRunId,
        String harnessRunId,
        Long requestedBy
) {
}

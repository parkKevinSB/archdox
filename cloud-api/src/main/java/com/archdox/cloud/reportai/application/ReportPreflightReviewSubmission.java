package com.archdox.cloud.reportai.application;

import com.archdox.cloud.reportai.dto.ReportPreflightReviewRunResponse;
import io.github.parkkevinsb.flower.core.flow.Flow;
import java.util.Objects;

public record ReportPreflightReviewSubmission(
        ReportPreflightReviewRunResponse response,
        Flow flow
) {
    public ReportPreflightReviewSubmission {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(flow, "flow must not be null");
    }
}

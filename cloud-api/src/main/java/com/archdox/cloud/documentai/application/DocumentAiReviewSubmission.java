package com.archdox.cloud.documentai.application;

import com.archdox.cloud.documentai.dto.DocumentAiReviewRunResponse;
import io.github.parkkevinsb.flower.core.flow.Flow;
import java.util.Objects;

public record DocumentAiReviewSubmission(
        DocumentAiReviewRunResponse response,
        Flow flow
) {
    public DocumentAiReviewSubmission {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(flow, "flow must not be null");
    }
}

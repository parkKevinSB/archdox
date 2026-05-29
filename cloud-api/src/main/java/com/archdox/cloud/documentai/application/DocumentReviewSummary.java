package com.archdox.cloud.documentai.application;

import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;

public record DocumentReviewSummary(
        DocumentReviewOutcome outcome,
        AiHarnessRunStatus harnessStatus,
        long findingCount
) {
}

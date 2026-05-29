package com.archdox.cloud.aipolicy.infra;

import java.math.BigDecimal;

public interface AiUsageGroupProjection {
    Long getOfficeId();

    String getFeature();

    Long getCallCount();

    Long getSucceededCount();

    Long getFailedCount();

    Long getInputTokens();

    Long getOutputTokens();

    BigDecimal getEstimatedTotalCost();
}

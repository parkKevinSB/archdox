package com.archdox.cloud.aipolicy.infra;

public interface AiUserUsageGroupProjection {
    Long getOfficeId();

    Long getUserId();

    Long getCallCount();

    Long getInputTokens();

    Long getOutputTokens();
}

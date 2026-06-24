package com.archdox.cloud.aiharness.application;

import java.time.OffsetDateTime;

public record AiObservabilityRetentionResult(
        boolean enabled,
        int retentionDays,
        OffsetDateTime cutoff,
        int deletedTraceEvents,
        int deletedModelCallLogs
) {
    public static AiObservabilityRetentionResult disabled(int retentionDays, OffsetDateTime cutoff) {
        return new AiObservabilityRetentionResult(false, retentionDays, cutoff, 0, 0);
    }
}

package com.archdox.cloud.legal.application;

import java.time.OffsetDateTime;

public record LegalSyncMonitorDecision(
        String status,
        String reason,
        String sourceCode,
        OffsetDateTime dueAt,
        Long syncRunId
) {
    public static LegalSyncMonitorDecision skipped(String reason, String sourceCode, OffsetDateTime dueAt) {
        return new LegalSyncMonitorDecision("SKIPPED", reason, sourceCode, dueAt, null);
    }

    public static LegalSyncMonitorDecision submitted(String sourceCode, OffsetDateTime dueAt, Long syncRunId) {
        return new LegalSyncMonitorDecision("SUBMITTED", "DUE", sourceCode, dueAt, syncRunId);
    }
}

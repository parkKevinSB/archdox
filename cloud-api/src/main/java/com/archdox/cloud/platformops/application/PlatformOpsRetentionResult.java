package com.archdox.cloud.platformops.application;

import java.time.OffsetDateTime;

public record PlatformOpsRetentionResult(
        boolean enabled,
        int retentionDays,
        OffsetDateTime cutoff,
        long deletedDailyReports,
        long deletedFindings,
        long deletedIncidents,
        long deletedRuns,
        long deletedLogProjectionEvents
) {
    public static PlatformOpsRetentionResult disabled(int retentionDays, OffsetDateTime cutoff) {
        return new PlatformOpsRetentionResult(false, retentionDays, cutoff, 0, 0, 0, 0, 0);
    }

    public long totalDeleted() {
        return deletedDailyReports + deletedFindings + deletedIncidents + deletedRuns + deletedLogProjectionEvents;
    }
}

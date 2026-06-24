package com.archdox.cloud.platformops.application;

public record PlatformOpsDetectionMonitorDecision(
        String status,
        String reason,
        Long opsRunId
) {
    public static PlatformOpsDetectionMonitorDecision skipped(String reason) {
        return new PlatformOpsDetectionMonitorDecision("SKIPPED", reason, null);
    }

    public static PlatformOpsDetectionMonitorDecision requested(Long opsRunId) {
        return new PlatformOpsDetectionMonitorDecision("REQUESTED", "DUE", opsRunId);
    }
}

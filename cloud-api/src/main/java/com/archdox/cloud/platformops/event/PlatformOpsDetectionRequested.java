package com.archdox.cloud.platformops.event;

public record PlatformOpsDetectionRequested(
        Long opsRunId,
        Long requestedByUserId
) {
}

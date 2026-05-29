package com.archdox.cloud.platformops.application;

import java.time.OffsetDateTime;

public record PlatformOpsDetectionSummary(
        Long opsRunId,
        int stuckDocumentJobs,
        int stuckAgentCommands,
        int stuckPhotoPickups,
        int stuckDeliveries,
        int incidentCount,
        int findingCount,
        OffsetDateTime detectedAt
) {
    public int total() {
        return stuckDocumentJobs + stuckAgentCommands + stuckPhotoPickups + stuckDeliveries;
    }
}

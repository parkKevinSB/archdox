package com.archdox.cloud.platformadmin.dto;

import java.time.OffsetDateTime;

public record PlatformHealthDetectionResponse(
        int stuckDocumentJobs,
        int stuckAgentCommands,
        int stuckPhotoPickups,
        int stuckDeliveries,
        OffsetDateTime detectedAt,
        int total
) {
    public PlatformHealthDetectionResponse(
            int stuckDocumentJobs,
            int stuckAgentCommands,
            int stuckPhotoPickups,
            int stuckDeliveries,
            OffsetDateTime detectedAt
    ) {
        this(
                stuckDocumentJobs,
                stuckAgentCommands,
                stuckPhotoPickups,
                stuckDeliveries,
                detectedAt,
                stuckDocumentJobs + stuckAgentCommands + stuckPhotoPickups + stuckDeliveries);
    }
}

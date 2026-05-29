package com.archdox.cloud.platformadmin.dto;

import java.time.OffsetDateTime;

public record PlatformHealthDetectionResponse(
        int stuckDocumentJobs,
        int stuckAgentCommands,
        int stuckPhotoPickups,
        int stuckDeliveries,
        OffsetDateTime detectedAt,
        int total,
        Long opsRunId,
        int incidentCount,
        int findingCount
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
                stuckDocumentJobs + stuckAgentCommands + stuckPhotoPickups + stuckDeliveries,
                null,
                stuckDocumentJobs + stuckAgentCommands + stuckPhotoPickups + stuckDeliveries,
                stuckDocumentJobs + stuckAgentCommands + stuckPhotoPickups + stuckDeliveries);
    }

    public PlatformHealthDetectionResponse(
            int stuckDocumentJobs,
            int stuckAgentCommands,
            int stuckPhotoPickups,
            int stuckDeliveries,
            OffsetDateTime detectedAt,
            Long opsRunId,
            int incidentCount,
            int findingCount
    ) {
        this(
                stuckDocumentJobs,
                stuckAgentCommands,
                stuckPhotoPickups,
                stuckDeliveries,
                detectedAt,
                stuckDocumentJobs + stuckAgentCommands + stuckPhotoPickups + stuckDeliveries,
                opsRunId,
                incidentCount,
                findingCount);
    }
}

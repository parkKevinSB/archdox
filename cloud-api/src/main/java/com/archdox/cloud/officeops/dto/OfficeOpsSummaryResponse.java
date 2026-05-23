package com.archdox.cloud.officeops.dto;

import java.time.OffsetDateTime;

public record OfficeOpsSummaryResponse(
        Long officeId,
        OpsCountGroupResponse agents,
        long activeAgentSessions,
        long inFlightAgentCommands,
        OpsCountGroupResponse documentJobs,
        OpsCountGroupResponse photos,
        OpsCountGroupResponse photoOriginalPickups,
        OpsCountGroupResponse documentDeliveries,
        OffsetDateTime generatedAt
) {
}

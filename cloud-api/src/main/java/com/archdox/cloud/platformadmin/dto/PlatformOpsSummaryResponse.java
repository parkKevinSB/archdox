package com.archdox.cloud.platformadmin.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record PlatformOpsSummaryResponse(
        long users,
        long offices,
        Map<String, Long> agents,
        long activeAgentSessions,
        Map<String, Long> agentCommands,
        Map<String, Long> documentJobs,
        Map<String, Long> photos,
        Map<String, Long> photoPickups,
        Map<String, Long> deliveries,
        OffsetDateTime generatedAt
) {
}

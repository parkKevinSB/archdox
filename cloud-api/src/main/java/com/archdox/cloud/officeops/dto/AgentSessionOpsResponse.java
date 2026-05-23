package com.archdox.cloud.officeops.dto;

import com.archdox.cloud.agent.domain.ArchDoxAgentSessionStatus;
import java.time.OffsetDateTime;

public record AgentSessionOpsResponse(
        Long id,
        Long agentId,
        String apiInstanceId,
        String websocketSessionId,
        ArchDoxAgentSessionStatus status,
        OffsetDateTime connectedAt,
        OffsetDateTime lastSeenAt,
        OffsetDateTime disconnectedAt,
        String disconnectReason
) {
}

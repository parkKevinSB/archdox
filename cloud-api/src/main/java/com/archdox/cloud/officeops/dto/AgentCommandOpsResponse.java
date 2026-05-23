package com.archdox.cloud.officeops.dto;

import com.archdox.cloud.agent.domain.ArchDoxAgentCommandStatus;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommandType;
import java.time.OffsetDateTime;

public record AgentCommandOpsResponse(
        Long id,
        Long agentId,
        String agentCode,
        ArchDoxAgentCommandType commandType,
        ArchDoxAgentCommandStatus status,
        int attemptCount,
        int maxAttempts,
        OffsetDateTime createdAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime ackAt,
        OffsetDateTime completedAt,
        OffsetDateTime failedAt,
        OffsetDateTime lastAttemptAt,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime expiresAt,
        String errorMessage
) {
}

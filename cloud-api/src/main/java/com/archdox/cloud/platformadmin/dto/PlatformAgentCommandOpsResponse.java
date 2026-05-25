package com.archdox.cloud.platformadmin.dto;

import com.archdox.cloud.agent.domain.ArchDoxAgentCommandStatus;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommandType;
import java.time.OffsetDateTime;

public record PlatformAgentCommandOpsResponse(
        Long id,
        Long officeId,
        Long agentId,
        String agentCode,
        ArchDoxAgentCommandType commandType,
        ArchDoxAgentCommandStatus status,
        int attemptCount,
        int maxAttempts,
        OffsetDateTime createdAt,
        OffsetDateTime lastAttemptAt,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime expiresAt,
        String errorMessage
) {
}

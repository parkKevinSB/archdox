package com.archdox.cloud.agent.dto;

import com.archdox.cloud.agent.domain.ArchDoxAgentInstallTokenStatus;
import java.time.OffsetDateTime;

public record ArchDoxAgentInstallTokenResponse(
        Long id,
        Long officeId,
        Long agentId,
        String agentCode,
        String deploymentMode,
        ArchDoxAgentInstallTokenStatus status,
        String token,
        OffsetDateTime expiresAt
) {
}

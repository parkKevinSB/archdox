package com.archdox.cloud.officeops.dto;

import com.archdox.cloud.agent.domain.ArchDoxAgentAuthMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentDeploymentMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AgentOpsResponse(
        Long id,
        Long officeId,
        String agentCode,
        ArchDoxAgentDeploymentMode deploymentMode,
        ArchDoxAgentStatus status,
        ArchDoxAgentAuthMode authMode,
        String version,
        OffsetDateTime lastSeenAt,
        OffsetDateTime lastAuthenticatedAt,
        OffsetDateTime pairedAt,
        OffsetDateTime registeredAt,
        int activeSessionCount,
        long inFlightCommandCount,
        long failedCommandCount,
        Map<String, Object> capabilities,
        Map<String, Object> compatibility,
        Map<String, Object> storageProfile,
        List<AgentSessionOpsResponse> activeSessions
) {
}

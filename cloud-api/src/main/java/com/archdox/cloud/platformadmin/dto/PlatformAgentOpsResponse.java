package com.archdox.cloud.platformadmin.dto;

import com.archdox.cloud.agent.domain.ArchDoxAgentDeploymentMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentStatus;
import java.time.OffsetDateTime;
import java.util.Map;

public record PlatformAgentOpsResponse(
        Long id,
        Long officeId,
        String agentCode,
        ArchDoxAgentDeploymentMode deploymentMode,
        ArchDoxAgentStatus status,
        String version,
        OffsetDateTime lastSeenAt,
        Map<String, Object> capabilities,
        Map<String, Object> compatibility,
        Map<String, Object> storageProfile
) {
}

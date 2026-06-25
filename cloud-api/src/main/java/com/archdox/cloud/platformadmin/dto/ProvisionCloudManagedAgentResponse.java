package com.archdox.cloud.platformadmin.dto;

import com.archdox.cloud.agent.domain.ArchDoxAgentAuthMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentDeploymentMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentStatus;
import java.time.OffsetDateTime;
import java.util.Map;

public record ProvisionCloudManagedAgentResponse(
        Long officeId,
        Long agentId,
        String agentCode,
        ArchDoxAgentDeploymentMode deploymentMode,
        ArchDoxAgentAuthMode authMode,
        ArchDoxAgentStatus status,
        String deviceSecret,
        OffsetDateTime pairedAt,
        Map<String, Object> storageProfile
) {
}

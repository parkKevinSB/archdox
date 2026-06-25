package com.archdox.cloud.platformadmin.dto;

import java.util.Map;

public record ProvisionCloudManagedAgentRequest(
        Long officeId,
        String agentCode,
        Map<String, Object> storageProfile
) {
}

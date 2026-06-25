package com.archdox.cloud.agent.application;

import java.util.Map;

public record AgentHello(
        String authMode,
        Long agentId,
        Long officeId,
        String agentCode,
        String token,
        String installToken,
        String deviceSecret,
        String version,
        String protocolVersion,
        String launcherVersion,
        String updateChannel,
        String deploymentMode,
        Map<String, Object> capabilities,
        Map<String, Object> storageProfile
) {
    public AgentHello(
            String authMode,
            Long agentId,
            Long officeId,
            String agentCode,
            String token,
            String installToken,
            String deviceSecret,
            String version,
            Map<String, Object> capabilities
    ) {
        this(authMode, agentId, officeId, agentCode, token, installToken, deviceSecret, version, null, null, null, null, capabilities, Map.of());
    }
}

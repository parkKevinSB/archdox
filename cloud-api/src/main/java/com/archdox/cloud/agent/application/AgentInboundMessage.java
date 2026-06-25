package com.archdox.cloud.agent.application;

import java.util.Map;

public record AgentInboundMessage(
        String type,
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
        Map<String, Object> storageProfile,
        Long commandId,
        Long diskFreeBytes,
        Integer pendingJobs,
        Integer recentErrorCount,
        Map<String, Object> result,
        String errorCode,
        Boolean retryable,
        String errorMessage
) {
    public AgentHello toHello() {
        return new AgentHello(
                authMode,
                agentId,
                officeId,
                agentCode,
                token,
                installToken,
                deviceSecret,
                version,
                protocolVersion,
                launcherVersion,
                updateChannel,
                deploymentMode,
                capabilities == null ? Map.of() : capabilities,
                storageProfile == null ? Map.of() : storageProfile);
    }

    public AgentHeartbeat toHeartbeat() {
        return new AgentHeartbeat(version, diskFreeBytes, pendingJobs, recentErrorCount);
    }
}

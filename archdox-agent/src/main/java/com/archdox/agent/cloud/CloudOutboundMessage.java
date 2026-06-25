package com.archdox.agent.cloud;

import java.util.Map;

public record CloudOutboundMessage(
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
    public static CloudOutboundMessage hello(ArchDoxAgentProperties properties, Map<String, Object> capabilities) {
        return new CloudOutboundMessage(
                "HELLO",
                properties.getAuthMode(),
                properties.getAgentId(),
                properties.getOfficeId(),
                properties.getAgentCode(),
                properties.getToken(),
                properties.getInstallToken(),
                properties.getDeviceSecret(),
                properties.getVersion(),
                properties.getProtocolVersion(),
                properties.getLauncherVersion(),
                properties.getUpdateChannel(),
                properties.getDeploymentMode(),
                capabilities == null ? Map.of() : capabilities,
                properties.storageProfile(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static CloudOutboundMessage heartbeat(ArchDoxAgentProperties properties) {
        return heartbeat(properties, 0, 0);
    }

    public static CloudOutboundMessage heartbeat(ArchDoxAgentProperties properties, int pendingJobs, int recentErrorCount) {
        return new CloudOutboundMessage(
                "HEARTBEAT",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                properties.getVersion(),
                properties.getProtocolVersion(),
                properties.getLauncherVersion(),
                properties.getUpdateChannel(),
                properties.getDeploymentMode(),
                null,
                properties.storageProfile(),
                null,
                null,
                Math.max(0, pendingJobs),
                Math.max(0, recentErrorCount),
                null,
                null,
                null,
                null);
    }

    public static CloudOutboundMessage ack(Long commandId) {
        return command(commandId, "ACK", Map.of(), null, null, null);
    }

    public static CloudOutboundMessage complete(Long commandId, Map<String, Object> result) {
        return command(commandId, "COMPLETE", result, null, null, null);
    }

    public static CloudOutboundMessage fail(Long commandId, String errorMessage) {
        return fail(commandId, new AgentCommandFailure("AGENT_COMMAND_FAILED", false, errorMessage));
    }

    public static CloudOutboundMessage fail(Long commandId, AgentCommandFailure failure) {
        return command(commandId, "FAIL", failure.result(), failure.errorCode(), failure.retryable(), failure.message());
    }

    private static CloudOutboundMessage command(
            Long commandId,
            String type,
            Map<String, Object> result,
            String errorCode,
            Boolean retryable,
            String errorMessage
    ) {
        return new CloudOutboundMessage(
                type,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                commandId,
                null,
                null,
                null,
                result,
                errorCode,
                retryable,
                errorMessage);
    }
}

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
        String deploymentMode,
        Map<String, Object> capabilities,
        Map<String, Object> storageProfile,
        Long commandId,
        Long diskFreeBytes,
        Integer pendingJobs,
        Integer recentErrorCount,
        Map<String, Object> result,
        String errorMessage
) {
    public static CloudOutboundMessage hello(ArchDoxAgentProperties properties) {
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
                properties.getDeploymentMode(),
                Map.of("nas", true, "photoPickup", true, "documentGeneration", true, "documentArtifactDelivery", true),
                properties.storageProfile(),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static CloudOutboundMessage heartbeat(ArchDoxAgentProperties properties) {
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
                properties.getDeploymentMode(),
                null,
                properties.storageProfile(),
                null,
                null,
                0,
                0,
                null,
                null);
    }

    public static CloudOutboundMessage ack(Long commandId) {
        return command(commandId, "ACK", Map.of(), null);
    }

    public static CloudOutboundMessage complete(Long commandId, Map<String, Object> result) {
        return command(commandId, "COMPLETE", result, null);
    }

    public static CloudOutboundMessage fail(Long commandId, String errorMessage) {
        return command(commandId, "FAIL", Map.of(), errorMessage);
    }

    private static CloudOutboundMessage command(
            Long commandId,
            String type,
            Map<String, Object> result,
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
                commandId,
                null,
                null,
                null,
                result,
                errorMessage);
    }
}

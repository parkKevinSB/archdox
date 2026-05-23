package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.domain.ArchDoxAgentCommandType;
import java.util.Map;

public record AgentOutboundMessage(
        String type,
        Long agentId,
        String authMode,
        String deviceSecret,
        Long commandId,
        ArchDoxAgentCommandType commandType,
        Map<String, Object> payload,
        String message
) {
    public static AgentOutboundMessage welcome(Long agentId, String deviceSecret) {
        return new AgentOutboundMessage(
                "WELCOME",
                agentId,
                deviceSecret == null ? "DEVICE_SECRET" : "INSTALL_TOKEN",
                deviceSecret,
                null,
                null,
                null,
                null);
    }

    public static AgentOutboundMessage command(Long commandId, ArchDoxAgentCommandType commandType, Map<String, Object> payload) {
        return new AgentOutboundMessage("COMMAND", null, null, null, commandId, commandType, payload, null);
    }

    public static AgentOutboundMessage error(String message) {
        return new AgentOutboundMessage("ERROR", null, null, null, null, null, null, message);
    }
}

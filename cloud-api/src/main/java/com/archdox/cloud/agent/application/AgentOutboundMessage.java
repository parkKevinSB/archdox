package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.domain.ArchDoxAgentCommandType;
import java.util.Map;

public record AgentOutboundMessage(
        String type,
        Long agentId,
        String authMode,
        String deviceSecret,
        Map<String, Object> aiPolicy,
        Long commandId,
        ArchDoxAgentCommandType commandType,
        Map<String, Object> payload,
        String message
) {
    public static AgentOutboundMessage welcome(Long agentId, String deviceSecret) {
        return welcome(agentId, deviceSecret, null);
    }

    public static AgentOutboundMessage welcome(Long agentId, String deviceSecret, Map<String, Object> aiPolicy) {
        return new AgentOutboundMessage(
                "WELCOME",
                agentId,
                deviceSecret == null ? "DEVICE_SECRET" : "INSTALL_TOKEN",
                deviceSecret,
                aiPolicy,
                null,
                null,
                null,
                null);
    }

    public static AgentOutboundMessage command(Long commandId, ArchDoxAgentCommandType commandType, Map<String, Object> payload) {
        return new AgentOutboundMessage("COMMAND", null, null, null, null, commandId, commandType, payload, null);
    }

    public static AgentOutboundMessage aiPolicyChanged(Long agentId, Map<String, Object> aiPolicy) {
        return new AgentOutboundMessage("AI_POLICY_CHANGED", agentId, null, null, aiPolicy, null, null, null, null);
    }

    public static AgentOutboundMessage error(String message) {
        return new AgentOutboundMessage("ERROR", null, null, null, null, null, null, null, message);
    }
}

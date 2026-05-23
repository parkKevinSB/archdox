package com.archdox.agent.cloud;

import java.util.Map;

public record CloudInboundMessage(
        String type,
        Long agentId,
        String authMode,
        String deviceSecret,
        Long commandId,
        String commandType,
        Map<String, Object> payload,
        String message
) {
}

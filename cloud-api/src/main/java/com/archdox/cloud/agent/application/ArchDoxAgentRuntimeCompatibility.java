package com.archdox.cloud.agent.application;

import java.util.LinkedHashMap;
import java.util.Map;

public record ArchDoxAgentRuntimeCompatibility(
        String status,
        boolean commandAllowed,
        boolean updateRequired,
        String reason,
        String agentVersion,
        String protocolVersion,
        String launcherVersion,
        String updateChannel,
        String currentProtocolVersion,
        String minimumProtocolVersion,
        String minimumAgentVersion,
        String recommendedAgentVersion
) {
    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("status", status);
        map.put("commandAllowed", commandAllowed);
        map.put("updateRequired", updateRequired);
        map.put("reason", reason);
        map.put("agentVersion", agentVersion);
        map.put("protocolVersion", protocolVersion);
        map.put("launcherVersion", launcherVersion);
        map.put("updateChannel", updateChannel);
        map.put("currentProtocolVersion", currentProtocolVersion);
        map.put("minimumProtocolVersion", minimumProtocolVersion);
        map.put("minimumAgentVersion", minimumAgentVersion);
        map.put("recommendedAgentVersion", recommendedAgentVersion);
        return map;
    }
}

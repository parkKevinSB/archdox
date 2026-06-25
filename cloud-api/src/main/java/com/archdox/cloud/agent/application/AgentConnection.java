package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.domain.ArchDoxAgent;

public record AgentConnection(
        ArchDoxAgent agent,
        String issuedDeviceSecret,
        ArchDoxAgentRuntimeCompatibility compatibility
) {
}

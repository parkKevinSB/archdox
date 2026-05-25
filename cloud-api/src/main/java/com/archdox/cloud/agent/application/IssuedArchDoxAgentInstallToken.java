package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentInstallToken;

public record IssuedArchDoxAgentInstallToken(
        ArchDoxAgent agent,
        ArchDoxAgentInstallToken installToken,
        String rawToken
) {
}

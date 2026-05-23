package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.domain.ArchDoxAgentInstallToken;

public record IssuedArchDoxAgentInstallToken(
        ArchDoxAgentInstallToken installToken,
        String rawToken
) {
}

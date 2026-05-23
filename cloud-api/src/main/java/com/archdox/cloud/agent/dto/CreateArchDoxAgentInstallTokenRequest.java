package com.archdox.cloud.agent.dto;

public record CreateArchDoxAgentInstallTokenRequest(
        Integer expiresInMinutes
) {
}

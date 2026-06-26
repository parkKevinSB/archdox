package com.archdox.agent.launcher;

public record AgentRuntimeState(
        String status,
        Long pid,
        String command,
        String healthUrl,
        String stdoutLog,
        String stderrLog,
        String reason,
        String updatedAt
) {
}

package com.archdox.agent.launcher;

public record AgentRuntimeStatusResult(
        String status,
        boolean pidKnown,
        boolean pidAlive,
        boolean healthConfirmed,
        Long pid,
        String healthUrl,
        AgentRuntimeState state,
        String reason
) {
}

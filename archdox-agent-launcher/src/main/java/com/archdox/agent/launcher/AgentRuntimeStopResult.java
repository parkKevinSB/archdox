package com.archdox.agent.launcher;

public record AgentRuntimeStopResult(
        String status,
        boolean attempted,
        boolean stopped,
        Long pid,
        String reason
) {
    public static AgentRuntimeStopResult notRunning(String reason) {
        return new AgentRuntimeStopResult("NOT_RUNNING", false, false, null, reason);
    }
}

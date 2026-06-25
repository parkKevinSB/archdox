package com.archdox.agent.launcher;

public record AgentUpdateDecision(
        String status,
        boolean runtimeUpdateRequired,
        boolean runtimeUpdateRecommended,
        boolean launcherUpdateRecommended,
        boolean canDownloadRuntime,
        String reason
) {
    public static AgentUpdateDecision ok() {
        return new AgentUpdateDecision(
                "OK",
                false,
                false,
                false,
                false,
                "Agent runtime and launcher are compatible.");
    }
}

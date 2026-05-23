package com.archdox.cloud.agent.application;

public record AgentHeartbeat(
        String version,
        Long diskFreeBytes,
        Integer pendingJobs,
        Integer recentErrorCount
) {
}

package com.archdox.agent.launcher;

public record AgentRuntimeStartResult(
        String status,
        boolean attempted,
        boolean started,
        boolean healthConfirmed,
        boolean rollbackAttempted,
        boolean rolledBack,
        Long pid,
        String command,
        String healthUrl,
        String stdoutLog,
        String stderrLog,
        String reason
) {
    public static AgentRuntimeStartResult notAttempted(String reason) {
        return new AgentRuntimeStartResult(
                "NOT_ATTEMPTED",
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                reason);
    }

    public static AgentRuntimeStartResult failed(
            String reason,
            boolean rollbackAttempted,
            boolean rolledBack
    ) {
        return new AgentRuntimeStartResult(
                "FAILED",
                true,
                false,
                false,
                rollbackAttempted,
                rolledBack,
                null,
                null,
                null,
                null,
                null,
                reason);
    }
}

package com.archdox.agent.launcher;

public record AgentRuntimeInstallResult(
        String status,
        boolean attempted,
        boolean installed,
        boolean restartRequired,
        String reason,
        String installedVersion,
        String packagePath,
        String currentPath,
        String previousPath
) {
    public static AgentRuntimeInstallResult notAttempted(String reason) {
        return new AgentRuntimeInstallResult(
                "NOT_ATTEMPTED",
                false,
                false,
                false,
                reason,
                null,
                null,
                null,
                null);
    }

    public static AgentRuntimeInstallResult skipped(String reason) {
        return new AgentRuntimeInstallResult(
                "SKIPPED",
                false,
                false,
                false,
                reason,
                null,
                null,
                null,
                null);
    }

    public static AgentRuntimeInstallResult failed(String reason) {
        return new AgentRuntimeInstallResult(
                "FAILED",
                true,
                false,
                false,
                reason,
                null,
                null,
                null,
                null);
    }
}

package com.archdox.agent.launcher;

public class AgentUpdatePlanner {
    public AgentUpdateDecision decide(
            AgentRuntimeManifest manifest,
            String currentAgentVersion,
            String currentProtocolVersion,
            String currentLauncherVersion
    ) {
        if (manifest == null) {
            return new AgentUpdateDecision(
                    "MANIFEST_UNAVAILABLE",
                    false,
                    false,
                    false,
                    false,
                    "Runtime manifest is unavailable.");
        }
        if (AgentVersionComparator.compareProtocol(currentProtocolVersion, manifest.minimumProtocolVersion()) < 0) {
            return new AgentUpdateDecision(
                    "UPDATE_REQUIRED",
                    true,
                    false,
                    launcherBehind(currentLauncherVersion, manifest.recommendedLauncherVersion()),
                    manifest.downloadAvailable(),
                    "Agent protocolVersion is below the minimum supported protocol.");
        }
        if (AgentVersionComparator.compareVersion(currentAgentVersion, manifest.minimumAgentVersion()) < 0) {
            return new AgentUpdateDecision(
                    "UPDATE_REQUIRED",
                    true,
                    false,
                    launcherBehind(currentLauncherVersion, manifest.recommendedLauncherVersion()),
                    manifest.downloadAvailable(),
                    "Agent runtime version is below the minimum supported version.");
        }
        var runtimeRecommended = AgentVersionComparator.compareVersion(
                currentAgentVersion,
                manifest.recommendedAgentVersion()) < 0;
        var launcherRecommended = launcherBehind(currentLauncherVersion, manifest.recommendedLauncherVersion());
        if (runtimeRecommended || launcherRecommended) {
            return new AgentUpdateDecision(
                    "UPDATE_RECOMMENDED",
                    false,
                    runtimeRecommended,
                    launcherRecommended,
                    manifest.downloadAvailable(),
                    runtimeRecommended
                            ? "A newer Agent runtime is recommended."
                            : "A newer Agent launcher is recommended.");
        }
        return AgentUpdateDecision.ok();
    }

    private boolean launcherBehind(String currentLauncherVersion, String recommendedLauncherVersion) {
        if ("embedded".equalsIgnoreCase(String.valueOf(currentLauncherVersion))
                || "embedded".equalsIgnoreCase(String.valueOf(recommendedLauncherVersion))) {
            return false;
        }
        return AgentVersionComparator.compareVersion(currentLauncherVersion, recommendedLauncherVersion) < 0;
    }
}

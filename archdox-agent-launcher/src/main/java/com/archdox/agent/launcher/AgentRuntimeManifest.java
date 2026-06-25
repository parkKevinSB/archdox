package com.archdox.agent.launcher;

public record AgentRuntimeManifest(
        String channel,
        String platform,
        String currentProtocolVersion,
        String minimumProtocolVersion,
        String minimumAgentVersion,
        String recommendedAgentVersion,
        String latestAgentVersion,
        String minimumLauncherVersion,
        String recommendedLauncherVersion,
        boolean downloadAvailable,
        String downloadUrl,
        String sha256,
        String signatureUrl,
        String releaseNotesUrl,
        String generatedAt
) {
}

package com.archdox.cloud.agent.dto;

import java.time.OffsetDateTime;

public record ArchDoxAgentRuntimeManifestResponse(
        String manifestVersion,
        String channel,
        String platform,
        String cloudApiVersion,
        String cloudApiGitCommit,
        String cloudApiBuildTime,
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
        OffsetDateTime generatedAt
) {
}

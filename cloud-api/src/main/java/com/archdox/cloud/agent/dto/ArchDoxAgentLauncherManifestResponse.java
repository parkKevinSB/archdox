package com.archdox.cloud.agent.dto;

import java.time.OffsetDateTime;

public record ArchDoxAgentLauncherManifestResponse(
        String manifestVersion,
        String channel,
        String platform,
        String cloudApiVersion,
        String cloudApiGitCommit,
        String cloudApiBuildTime,
        String minimumLauncherVersion,
        String recommendedLauncherVersion,
        String latestLauncherVersion,
        boolean downloadAvailable,
        String downloadUrl,
        String sha256,
        String signatureUrl,
        String releaseNotesUrl,
        OffsetDateTime generatedAt
) {
}

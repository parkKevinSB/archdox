package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.dto.ArchDoxAgentLauncherManifestResponse;
import com.archdox.cloud.system.application.ArchDoxCloudBuildInfoService;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ArchDoxAgentLauncherManifestService {
    private final ArchDoxAgentProperties agentProperties;
    private final ArchDoxAgentReleaseProperties releaseProperties;
    private final ArchDoxAgentReleaseUrlFactory releaseUrlFactory;
    private final ArchDoxCloudBuildInfoService buildInfoService;

    public ArchDoxAgentLauncherManifestService(
            ArchDoxAgentProperties agentProperties,
            ArchDoxAgentReleaseProperties releaseProperties,
            ArchDoxAgentReleaseUrlFactory releaseUrlFactory,
            ArchDoxCloudBuildInfoService buildInfoService
    ) {
        this.agentProperties = agentProperties;
        this.releaseProperties = releaseProperties;
        this.releaseUrlFactory = releaseUrlFactory;
        this.buildInfoService = buildInfoService;
    }

    public ArchDoxAgentLauncherManifestResponse manifest(String channel, String platform) {
        var safeChannel = normalized(channel, "stable");
        var safePlatform = normalized(platform, "windows-x64");
        var latestLauncherVersion = agentProperties.safeLatestLauncherVersion();
        var downloadUrl = releaseProperties.optionalLauncherPackageDownloadUrl();
        if (downloadUrl == null) {
            downloadUrl = releaseUrlFactory.launcherPackageUrl(safeChannel, safePlatform, latestLauncherVersion);
        }
        var sha256 = releaseProperties.optionalLauncherPackageSha256();
        var build = buildInfoService.current();
        return new ArchDoxAgentLauncherManifestResponse(
                "2026-06-26",
                safeChannel,
                safePlatform,
                build.version(),
                build.gitCommit(),
                build.buildTime(),
                agentProperties.safeMinimumLauncherVersion(),
                agentProperties.safeRecommendedLauncherVersion(),
                latestLauncherVersion,
                downloadUrl != null && sha256 != null,
                downloadUrl,
                sha256,
                releaseProperties.optionalLauncherPackageSignatureUrl(),
                releaseProperties.optionalLauncherReleaseNotesUrl(),
                OffsetDateTime.now());
    }

    private String normalized(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

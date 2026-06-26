package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.dto.ArchDoxAgentRuntimeManifestResponse;
import com.archdox.cloud.system.application.ArchDoxCloudBuildInfoService;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ArchDoxAgentRuntimeManifestService {
    private final ArchDoxAgentProperties properties;
    private final ArchDoxCloudBuildInfoService buildInfoService;

    public ArchDoxAgentRuntimeManifestService(
            ArchDoxAgentProperties properties,
            ArchDoxCloudBuildInfoService buildInfoService
    ) {
        this.properties = properties;
        this.buildInfoService = buildInfoService;
    }

    public ArchDoxAgentRuntimeManifestResponse manifest(String channel, String platform) {
        var safeChannel = normalized(channel, "stable");
        var safePlatform = normalized(platform, "windows-x64");
        var downloadUrl = properties.optionalRuntimePackageDownloadUrl();
        var sha256 = properties.optionalRuntimePackageSha256();
        var build = buildInfoService.current();
        return new ArchDoxAgentRuntimeManifestResponse(
                "2026-06-26",
                safeChannel,
                safePlatform,
                build.version(),
                build.gitCommit(),
                build.buildTime(),
                properties.safeCurrentProtocolVersion(),
                properties.safeMinimumProtocolVersion(),
                properties.safeMinimumAgentVersion(),
                properties.safeRecommendedAgentVersion(),
                properties.safeLatestAgentVersion(),
                properties.safeMinimumLauncherVersion(),
                properties.safeRecommendedLauncherVersion(),
                downloadUrl != null && sha256 != null,
                downloadUrl,
                sha256,
                properties.optionalRuntimePackageSignatureUrl(),
                properties.optionalRuntimeReleaseNotesUrl(),
                OffsetDateTime.now());
    }

    private String normalized(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

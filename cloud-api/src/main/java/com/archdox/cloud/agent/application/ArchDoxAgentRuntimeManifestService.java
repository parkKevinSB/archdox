package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.dto.ArchDoxAgentRuntimeManifestResponse;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ArchDoxAgentRuntimeManifestService {
    private final ArchDoxAgentProperties properties;

    public ArchDoxAgentRuntimeManifestService(ArchDoxAgentProperties properties) {
        this.properties = properties;
    }

    public ArchDoxAgentRuntimeManifestResponse manifest(String channel, String platform) {
        var safeChannel = normalized(channel, "stable");
        var safePlatform = normalized(platform, "windows-x64");
        var downloadUrl = properties.optionalRuntimePackageDownloadUrl();
        var sha256 = properties.optionalRuntimePackageSha256();
        return new ArchDoxAgentRuntimeManifestResponse(
                safeChannel,
                safePlatform,
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

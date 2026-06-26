package com.archdox.cloud.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.archdox.cloud.system.application.ArchDoxCloudBuildInfoService;
import org.junit.jupiter.api.Test;

class ArchDoxAgentRuntimeManifestServiceTest {
    @Test
    void blankPackageMetadataDoesNotEnableDownload() {
        var properties = new ArchDoxAgentProperties();
        var releases = new ArchDoxAgentReleaseProperties();
        var service = new ArchDoxAgentRuntimeManifestService(
                properties,
                new ArchDoxAgentReleaseUrlFactory(releases),
                new ArchDoxCloudBuildInfoService());

        var manifest = service.manifest("stable", "windows-x64");

        assertThat(manifest.manifestVersion()).isEqualTo("2026-06-26");
        assertThat(manifest.cloudApiVersion()).isNotBlank();
        assertThat(manifest.downloadAvailable()).isFalse();
        assertThat(manifest.downloadUrl()).isNull();
        assertThat(manifest.sha256()).isNull();
    }

    @Test
    void downloadIsAvailableOnlyWhenUrlAndChecksumAreConfigured() {
        var properties = new ArchDoxAgentProperties();
        properties.setRuntimePackageDownloadUrl("https://downloads.archdox.co.kr/agent.zip");
        properties.setRuntimePackageSha256("abc123");
        var releases = new ArchDoxAgentReleaseProperties();
        var service = new ArchDoxAgentRuntimeManifestService(
                properties,
                new ArchDoxAgentReleaseUrlFactory(releases),
                new ArchDoxCloudBuildInfoService());

        var manifest = service.manifest("STABLE", "WINDOWS-X64");

        assertThat(manifest.channel()).isEqualTo("stable");
        assertThat(manifest.platform()).isEqualTo("windows-x64");
        assertThat(manifest.downloadAvailable()).isTrue();
        assertThat(manifest.downloadUrl()).isEqualTo("https://downloads.archdox.co.kr/agent.zip");
        assertThat(manifest.sha256()).isEqualTo("abc123");
    }

    @Test
    void releaseBaseUrlBuildsRuntimeDownloadUrlWhenExplicitUrlIsAbsent() {
        var properties = new ArchDoxAgentProperties();
        properties.setLatestAgentVersion("1.2.3");
        properties.setRuntimePackageSha256("abc123");
        var releases = new ArchDoxAgentReleaseProperties();
        releases.setPublicBaseUrl("https://downloads.archdox.co.kr/");
        var service = new ArchDoxAgentRuntimeManifestService(
                properties,
                new ArchDoxAgentReleaseUrlFactory(releases),
                new ArchDoxCloudBuildInfoService());

        var manifest = service.manifest("stable", "windows-x64");

        assertThat(manifest.downloadAvailable()).isTrue();
        assertThat(manifest.downloadUrl())
                .isEqualTo("https://downloads.archdox.co.kr/releases/agent-runtime/stable/windows-x64/1.2.3/archdox-agent-runtime-windows-x64-1.2.3.zip");
    }
}

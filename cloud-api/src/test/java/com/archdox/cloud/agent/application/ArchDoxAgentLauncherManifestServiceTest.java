package com.archdox.cloud.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.archdox.cloud.system.application.ArchDoxCloudBuildInfoService;
import org.junit.jupiter.api.Test;

class ArchDoxAgentLauncherManifestServiceTest {
    @Test
    void releaseBaseUrlBuildsLauncherDownloadUrl() {
        var agentProperties = new ArchDoxAgentProperties();
        agentProperties.setLatestLauncherVersion("1.2.3");
        var releaseProperties = new ArchDoxAgentReleaseProperties();
        releaseProperties.setPublicBaseUrl("https://downloads.archdox.co.kr");
        releaseProperties.setLauncherPackageSha256("abc123");
        var service = new ArchDoxAgentLauncherManifestService(
                agentProperties,
                releaseProperties,
                new ArchDoxAgentReleaseUrlFactory(releaseProperties),
                new ArchDoxCloudBuildInfoService());

        var manifest = service.manifest("STABLE", "WINDOWS-X64");

        assertThat(manifest.channel()).isEqualTo("stable");
        assertThat(manifest.platform()).isEqualTo("windows-x64");
        assertThat(manifest.downloadAvailable()).isTrue();
        assertThat(manifest.downloadUrl())
                .isEqualTo("https://downloads.archdox.co.kr/releases/agent-launcher/stable/windows-x64/1.2.3/archdox-agent-launcher-windows-x64-1.2.3.zip");
        assertThat(manifest.sha256()).isEqualTo("abc123");
    }

    @Test
    void explicitLauncherUrlWinsOverReleaseBaseUrl() {
        var agentProperties = new ArchDoxAgentProperties();
        var releaseProperties = new ArchDoxAgentReleaseProperties();
        releaseProperties.setPublicBaseUrl("https://downloads.archdox.co.kr");
        releaseProperties.setLauncherPackageDownloadUrl("https://cdn.archdox.co.kr/custom/launcher.zip");
        releaseProperties.setLauncherPackageSha256("abc123");
        var service = new ArchDoxAgentLauncherManifestService(
                agentProperties,
                releaseProperties,
                new ArchDoxAgentReleaseUrlFactory(releaseProperties),
                new ArchDoxCloudBuildInfoService());

        var manifest = service.manifest("stable", "windows-x64");

        assertThat(manifest.downloadUrl()).isEqualTo("https://cdn.archdox.co.kr/custom/launcher.zip");
        assertThat(manifest.downloadAvailable()).isTrue();
    }
}

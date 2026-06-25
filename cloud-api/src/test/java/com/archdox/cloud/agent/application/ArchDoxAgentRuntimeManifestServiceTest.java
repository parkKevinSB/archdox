package com.archdox.cloud.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArchDoxAgentRuntimeManifestServiceTest {
    @Test
    void blankPackageMetadataDoesNotEnableDownload() {
        var properties = new ArchDoxAgentProperties();
        var service = new ArchDoxAgentRuntimeManifestService(properties);

        var manifest = service.manifest("stable", "windows-x64");

        assertThat(manifest.downloadAvailable()).isFalse();
        assertThat(manifest.downloadUrl()).isNull();
        assertThat(manifest.sha256()).isNull();
    }

    @Test
    void downloadIsAvailableOnlyWhenUrlAndChecksumAreConfigured() {
        var properties = new ArchDoxAgentProperties();
        properties.setRuntimePackageDownloadUrl("https://downloads.archdox.co.kr/agent.zip");
        properties.setRuntimePackageSha256("abc123");
        var service = new ArchDoxAgentRuntimeManifestService(properties);

        var manifest = service.manifest("STABLE", "WINDOWS-X64");

        assertThat(manifest.channel()).isEqualTo("stable");
        assertThat(manifest.platform()).isEqualTo("windows-x64");
        assertThat(manifest.downloadAvailable()).isTrue();
        assertThat(manifest.downloadUrl()).isEqualTo("https://downloads.archdox.co.kr/agent.zip");
        assertThat(manifest.sha256()).isEqualTo("abc123");
    }
}

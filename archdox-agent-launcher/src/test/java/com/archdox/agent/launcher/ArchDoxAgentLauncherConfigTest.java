package com.archdox.agent.launcher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArchDoxAgentLauncherConfigTest {
    @Test
    void parsesForceInstallOption() {
        var config = ArchDoxAgentLauncher.LauncherConfig.from(new String[] {
                "--force-install",
                "--cloud-api-base-url",
                "https://api.archdox.co.kr"
        });

        assertThat(config.forceInstall()).isTrue();
        assertThat(config.cloudApiBaseUrl()).isEqualTo("https://api.archdox.co.kr");
    }
}

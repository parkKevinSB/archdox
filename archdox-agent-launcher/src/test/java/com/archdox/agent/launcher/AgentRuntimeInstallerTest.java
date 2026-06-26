package com.archdox.agent.launcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentRuntimeInstallerTest {
    private final AgentChecksumVerifier checksumVerifier = new AgentChecksumVerifier();
    private final AgentRuntimeInstaller installer = new AgentRuntimeInstaller();

    @Test
    void installsVerifiedRuntimePackageAndMovesPreviousRuntime(@TempDir Path tempDir) throws Exception {
        var packageFile = tempDir.resolve("runtime.zip");
        createRuntimeZip(packageFile, "archdox-agent/bin/archdox-agent.bat", "echo new agent");
        var installDir = tempDir.resolve("install");
        var current = installDir.resolve("current");
        Files.createDirectories(current.resolve("bin"));
        Files.writeString(current.resolve("bin/archdox-agent.bat"), "echo old agent", StandardCharsets.UTF_8);

        var result = installer.install(
                manifest(packageFile.toUri(), checksumVerifier.sha256(packageFile)),
                installDir,
                tempDir.resolve("work"),
                null);

        assertThat(result.status()).isEqualTo("INSTALLED");
        assertThat(result.installed()).isTrue();
        assertThat(Files.readString(installDir.resolve("current/bin/archdox-agent.bat")))
                .isEqualTo("echo new agent");
        assertThat(Files.readString(installDir.resolve("previous/bin/archdox-agent.bat")))
                .isEqualTo("echo old agent");
        assertThat(Files.readString(installDir.resolve("current/.archdox-agent-version")))
                .contains("1.2.3");
    }

    @Test
    void rejectsRuntimePackageWhenChecksumDoesNotMatch(@TempDir Path tempDir) throws Exception {
        var packageFile = tempDir.resolve("runtime.zip");
        createRuntimeZip(packageFile, "archdox-agent/bin/archdox-agent.bat", "echo new agent");

        assertThatThrownBy(() -> installer.install(
                manifest(packageFile.toUri(), "0000"),
                tempDir.resolve("install"),
                tempDir.resolve("work"),
                null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum mismatch");
    }

    @Test
    void rejectsZipSlipEntries(@TempDir Path tempDir) throws Exception {
        var packageFile = tempDir.resolve("runtime.zip");
        createRuntimeZip(packageFile, "../evil.txt", "bad");

        assertThatThrownBy(() -> installer.install(
                manifest(packageFile.toUri(), checksumVerifier.sha256(packageFile)),
                tempDir.resolve("install"),
                tempDir.resolve("work"),
                null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsafe zip entry");
    }

    private AgentRuntimeManifest manifest(URI packageUri, String sha256) {
        return new AgentRuntimeManifest(
                "2026-06-26",
                "stable",
                "windows-x64",
                "0.0.1-SNAPSHOT",
                "test-commit",
                "2026-06-26T00:00:00Z",
                "2026-06-25",
                "2026-06-25",
                "1.2.3",
                "1.2.3",
                "1.2.3",
                "embedded",
                "embedded",
                true,
                packageUri.toString(),
                sha256,
                null,
                null,
                "2026-06-26T00:00:00Z");
    }

    private void createRuntimeZip(Path zipFile, String entryName, String content) throws IOException {
        try (var output = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}

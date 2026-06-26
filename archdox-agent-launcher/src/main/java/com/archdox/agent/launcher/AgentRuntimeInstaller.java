package com.archdox.agent.launcher;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public class AgentRuntimeInstaller {
    private final AgentPackageDownloader downloader;
    private final AgentChecksumVerifier checksumVerifier;
    private final AgentSignatureVerifier signatureVerifier;
    private final AgentZipExtractor zipExtractor;

    public AgentRuntimeInstaller() {
        this(
                new AgentPackageDownloader(),
                new AgentChecksumVerifier(),
                new AgentSignatureVerifier(),
                new AgentZipExtractor());
    }

    AgentRuntimeInstaller(
            AgentPackageDownloader downloader,
            AgentChecksumVerifier checksumVerifier,
            AgentSignatureVerifier signatureVerifier,
            AgentZipExtractor zipExtractor
    ) {
        this.downloader = downloader;
        this.checksumVerifier = checksumVerifier;
        this.signatureVerifier = signatureVerifier;
        this.zipExtractor = zipExtractor;
    }

    public AgentRuntimeInstallResult install(
            AgentRuntimeManifest manifest,
            Path installDir,
            Path workDir,
            Path signaturePublicKeyPath
    ) throws IOException, InterruptedException {
        if (manifest == null || !manifest.downloadAvailable()) {
            return AgentRuntimeInstallResult.skipped("Runtime manifest does not provide a downloadable package.");
        }
        var version = text(manifest.latestAgentVersion(), manifest.recommendedAgentVersion());
        if (version == null) {
            version = "unknown";
        }
        var safeVersion = version.replaceAll("[^A-Za-z0-9._-]", "_");
        var runId = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(OffsetDateTime.now());
        var downloadsDir = workDir.resolve("downloads");
        var stagingRoot = workDir.resolve("staging").resolve(safeVersion + "-" + runId);
        var packageFile = downloadsDir.resolve("archdox-agent-" + safeVersion + ".zip");
        var signatureFile = downloadsDir.resolve("archdox-agent-" + safeVersion + ".zip.sig");
        Files.createDirectories(downloadsDir);
        Files.createDirectories(stagingRoot);

        downloader.download(URI.create(manifest.downloadUrl()), packageFile);
        checksumVerifier.verifySha256(packageFile, manifest.sha256());
        if (text(manifest.signatureUrl(), null) != null) {
            downloader.download(URI.create(manifest.signatureUrl()), signatureFile);
            signatureVerifier.verify(packageFile, signatureFile, signaturePublicKeyPath);
        }

        var extractionRoot = stagingRoot.resolve("extracted");
        zipExtractor.extract(packageFile, extractionRoot);
        var payloadRoot = payloadRoot(extractionRoot);

        Files.createDirectories(installDir);
        var current = installDir.resolve("current");
        var previous = installDir.resolve("previous");
        deleteRecursively(previous);
        if (Files.exists(current)) {
            moveDirectory(current, previous);
        }
        try {
            moveDirectory(payloadRoot, current);
        } catch (IOException ex) {
            if (!Files.exists(current) && Files.exists(previous)) {
                moveDirectory(previous, current);
            }
            throw ex;
        }
        Files.writeString(
                current.resolve(".archdox-agent-version"),
                version + System.lineSeparator(),
                StandardCharsets.UTF_8);

        return new AgentRuntimeInstallResult(
                "INSTALLED",
                true,
                true,
                true,
                "Runtime package was verified and installed. Restart the Agent runtime from currentPath.",
                version,
                packageFile.toAbsolutePath().toString(),
                current.toAbsolutePath().toString(),
                Files.exists(previous) ? previous.toAbsolutePath().toString() : null);
    }

    private Path payloadRoot(Path extractionRoot) throws IOException {
        try (var stream = Files.list(extractionRoot)) {
            var children = stream.toList();
            var directories = children.stream().filter(Files::isDirectory).toList();
            var files = children.stream().filter(Files::isRegularFile).toList();
            if (directories.size() == 1 && files.isEmpty()) {
                return directories.getFirst();
            }
            return extractionRoot;
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path candidate : paths) {
                Files.deleteIfExists(candidate);
            }
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

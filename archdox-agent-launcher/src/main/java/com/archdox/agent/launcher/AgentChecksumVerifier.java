package com.archdox.agent.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class AgentChecksumVerifier {
    public void verifySha256(Path file, String expectedSha256) throws IOException {
        var expected = normalize(expectedSha256);
        if (expected == null) {
            throw new IllegalArgumentException("Runtime package sha256 is required before installation.");
        }
        var actual = sha256(file);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Runtime package checksum mismatch. expected="
                    + expected + ", actual=" + actual);
        }
    }

    public String sha256(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                var buffer = new byte[8192];
                for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.", ex);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalized = value.trim().toLowerCase();
        if (normalized.startsWith("sha256:")) {
            normalized = normalized.substring("sha256:".length());
        }
        return normalized.replaceAll("[^a-f0-9]", "");
    }
}

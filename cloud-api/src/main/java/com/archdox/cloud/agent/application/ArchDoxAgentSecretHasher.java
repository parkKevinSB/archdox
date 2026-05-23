package com.archdox.cloud.agent.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class ArchDoxAgentSecretHasher {
    private static final int SECRET_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSecret() {
        var bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        return HexFormat.of().formatHex(sha256(secret.trim()));
    }

    public boolean matches(String rawSecret, String expectedHash) {
        if (rawSecret == null || rawSecret.isBlank() || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(rawSecret).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }
}

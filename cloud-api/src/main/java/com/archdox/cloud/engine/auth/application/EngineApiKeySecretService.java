package com.archdox.cloud.engine.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import org.springframework.stereotype.Service;

@Service
public class EngineApiKeySecretService {
    public static final String KEY_PREFIX = "adx_live_";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public GeneratedKey generate() {
        var keyId = randomHex(16);
        var secret = randomHex(32);
        return new GeneratedKey(keyId, KEY_PREFIX + keyId + "_", KEY_PREFIX + keyId + "_" + secret, hash(secret));
    }

    public ParsedKey parse(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalArgumentException("Engine API key is required");
        }
        var trimmed = rawKey.trim();
        if (!trimmed.startsWith(KEY_PREFIX)) {
            throw new IllegalArgumentException("Invalid Engine API key prefix");
        }
        var rest = trimmed.substring(KEY_PREFIX.length());
        var separator = rest.indexOf('_');
        if (separator <= 0 || separator == rest.length() - 1) {
            throw new IllegalArgumentException("Invalid Engine API key format");
        }
        var keyId = rest.substring(0, separator);
        var secret = rest.substring(separator + 1);
        if (!hexLike(keyId) || !hexLike(secret)) {
            throw new IllegalArgumentException("Invalid Engine API key material");
        }
        return new ParsedKey(keyId, hash(secret));
    }

    public boolean matches(String expectedHash, String actualHash) {
        if (expectedHash == null || actualHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                actualHash.getBytes(StandardCharsets.UTF_8));
    }

    private String hash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return hex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String randomHex(int byteLength) {
        var bytes = new byte[byteLength];
        random.nextBytes(bytes);
        return hex(bytes);
    }

    private String hex(byte[] bytes) {
        var chars = new char[bytes.length * 2];
        for (var i = 0; i < bytes.length; i++) {
            var value = bytes[i] & 0xff;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(chars);
    }

    private boolean hexLike(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (var i = 0; i < value.length(); i++) {
            var c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    public record GeneratedKey(
            String keyId,
            String keyPrefix,
            String apiKey,
            String secretHash
    ) {
    }

    public record ParsedKey(
            String keyId,
            String secretHash
    ) {
    }
}

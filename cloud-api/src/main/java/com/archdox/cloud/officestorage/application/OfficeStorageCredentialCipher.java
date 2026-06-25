package com.archdox.cloud.officestorage.application;

import com.archdox.cloud.aipolicy.application.AiCredentialProperties;
import com.archdox.cloud.global.api.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class OfficeStorageCredentialCipher {
    private static final String PREFIX = "v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final OfficeStorageCredentialProperties properties;
    private final AiCredentialProperties aiCredentialProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public OfficeStorageCredentialCipher(
            OfficeStorageCredentialProperties properties,
            AiCredentialProperties aiCredentialProperties
    ) {
        this.properties = properties;
        this.aiCredentialProperties = aiCredentialProperties;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            var iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            var ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + ":"
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Office storage credential encryption failed", ex);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            var parts = encrypted.split(":", 3);
            if (parts.length != 3 || !PREFIX.equals(parts[0])) {
                throw new BadRequestException("Unsupported office storage credential ciphertext format");
            }
            var iv = Base64.getDecoder().decode(parts[1]);
            var ciphertext = Base64.getDecoder().decode(parts[2]);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Office storage credential decryption failed", ex);
        }
    }

    public String fingerprint(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        return hex(sha256(secret)).substring(0, 16);
    }

    public String maskFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        var trimmed = fingerprint.trim();
        var suffix = trimmed.length() <= 6 ? trimmed : trimmed.substring(trimmed.length() - 6);
        return "sha256:..." + suffix;
    }

    private SecretKeySpec keySpec() {
        var masterKey = firstText(properties.getMasterKey(), aiCredentialProperties.getMasterKey());
        if (masterKey == null) {
            throw new BadRequestException(
                    "OFFICE_STORAGE_CREDENTIAL_MASTER_KEY_REQUIRED",
                    "error.officeStorageCredential.masterKeyRequired",
                    "Office storage credential master key is not configured");
        }
        return new SecretKeySpec(sha256(masterKey), "AES");
    }

    private String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String hex(byte[] bytes) {
        var builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}

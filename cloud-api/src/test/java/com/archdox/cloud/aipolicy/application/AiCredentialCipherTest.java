package com.archdox.cloud.aipolicy.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiCredentialCipherTest {
    @Test
    void encryptsDecryptsAndMasksCredentialFingerprint() {
        var properties = new AiCredentialProperties();
        properties.setMasterKey("test-master-key");
        var cipher = new AiCredentialCipher(properties);

        var encrypted = cipher.encrypt("sk-test-secret");

        assertThat(encrypted).startsWith("v1:");
        assertThat(encrypted).doesNotContain("sk-test-secret");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("sk-test-secret");
        assertThat(cipher.fingerprint("sk-test-secret")).hasSize(16);
        assertThat(cipher.maskFingerprint(cipher.fingerprint("sk-test-secret"))).startsWith("sha256:...");
    }
}

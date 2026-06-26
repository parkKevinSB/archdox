package com.archdox.agent.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class AgentSignatureVerifier {
    public void verify(Path packageFile, Path signatureFile, Path publicKeyPath) throws IOException {
        if (publicKeyPath == null) {
            throw new IllegalArgumentException("Signature URL is configured, but no public key path was provided.");
        }
        try {
            var keyBytes = readPublicKey(publicKeyPath);
            var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(keyBytes));
            var signature = Signature.getInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(Files.readAllBytes(packageFile));
            if (!signature.verify(readSignature(signatureFile))) {
                throw new IllegalStateException("Runtime package signature verification failed.");
            }
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Runtime package signature verification failed.", ex);
        }
    }

    private byte[] readPublicKey(Path path) throws IOException {
        var text = Files.readString(path, StandardCharsets.UTF_8);
        var normalized = text
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private byte[] readSignature(Path path) throws IOException {
        var bytes = Files.readAllBytes(path);
        var text = new String(bytes, StandardCharsets.UTF_8).trim();
        if (text.matches("[A-Za-z0-9+/=\\r\\n\\t ]+")) {
            try {
                return Base64.getMimeDecoder().decode(text);
            } catch (IllegalArgumentException ignored) {
                return bytes;
            }
        }
        return bytes;
    }
}

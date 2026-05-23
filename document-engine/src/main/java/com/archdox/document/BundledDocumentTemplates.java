package com.archdox.document;

import java.io.IOException;
import java.util.Optional;

public final class BundledDocumentTemplates {
    private BundledDocumentTemplates() {
    }

    public static Optional<byte[]> read(String storageRef) throws IOException {
        var normalized = normalize(storageRef);
        if (normalized == null) {
            return Optional.empty();
        }
        var loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = BundledDocumentTemplates.class.getClassLoader();
        }
        try (var input = loader.getResourceAsStream(normalized)) {
            if (input == null) {
                return Optional.empty();
            }
            return Optional.of(input.readAllBytes());
        }
    }

    private static String normalize(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) {
            return null;
        }
        var normalized = storageRef.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.isBlank() ? null : normalized;
    }
}

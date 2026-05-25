package com.archdox.agent.storage;

import java.util.Arrays;

final class AgentStorageRefNormalizer {
    private AgentStorageRefNormalizer() {
    }

    static String normalize(String usage, String logicalRef) {
        if (logicalRef == null || logicalRef.isBlank()) {
            throw new IllegalArgumentException("ArchDox Agent " + usage + " storage reference is required");
        }
        var normalized = logicalRef.trim()
                .replace('\\', '/')
                .replaceFirst("^/+", "");
        if (normalized.isBlank()
                || normalized.contains("//")
                || normalized.endsWith("/")
                || hasTraversalSegment(normalized)) {
            throw new IllegalArgumentException("Invalid ArchDox Agent " + usage + " storage reference");
        }
        return normalized;
    }

    private static boolean hasTraversalSegment(String value) {
        return Arrays.stream(value.split("/"))
                .anyMatch(segment -> segment.isBlank() || ".".equals(segment) || "..".equals(segment));
    }
}

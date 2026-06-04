package com.archdox.cloud.engine.context;

public record ArchDoxCanonicalContextValue(
        String fieldName,
        String canonicalValue,
        String rawValue,
        double confidence
) {
    public ArchDoxCanonicalContextValue {
        fieldName = fieldName == null ? "" : fieldName.trim();
        canonicalValue = canonicalValue == null ? "" : canonicalValue.trim();
        rawValue = rawValue == null ? "" : rawValue.trim();
        confidence = Math.max(0.0d, Math.min(1.0d, confidence));
    }
}

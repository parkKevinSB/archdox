package com.archdox.cloud.engine.context;

public record ArchDoxContextFact(
        String fieldName,
        String rawValue,
        ArchDoxContextFactSource source,
        String evidence,
        double confidence
) {
    public ArchDoxContextFact {
        fieldName = fieldName == null ? "" : fieldName.trim();
        rawValue = rawValue == null ? "" : rawValue.trim();
        source = source == null ? ArchDoxContextFactSource.CUSTOMER_AGENT_EXTRACTED : source;
        evidence = evidence == null ? "" : evidence.trim();
        confidence = Math.max(0.0d, Math.min(1.0d, confidence));
    }
}

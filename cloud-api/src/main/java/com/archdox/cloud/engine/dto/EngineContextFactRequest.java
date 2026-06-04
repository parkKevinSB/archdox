package com.archdox.cloud.engine.dto;

public record EngineContextFactRequest(
        String name,
        String fieldName,
        String rawValue,
        String source,
        String evidence,
        Double confidence
) {
    public String resolvedFieldName() {
        if (fieldName != null && !fieldName.isBlank()) {
            return fieldName.trim();
        }
        return name == null ? "" : name.trim();
    }
}

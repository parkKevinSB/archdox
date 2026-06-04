package com.archdox.cloud.engine.context;

import java.util.List;

public record ArchDoxContextAmbiguity(
        String fieldName,
        String rawValue,
        List<String> candidates,
        String question
) {
    public ArchDoxContextAmbiguity {
        fieldName = fieldName == null ? "" : fieldName.trim();
        rawValue = rawValue == null ? "" : rawValue.trim();
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        question = question == null ? "" : question.trim();
    }
}

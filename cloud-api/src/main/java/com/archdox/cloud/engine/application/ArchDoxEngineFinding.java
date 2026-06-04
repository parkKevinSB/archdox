package com.archdox.cloud.engine.application;

import java.util.List;
import java.util.Map;

public record ArchDoxEngineFinding(
        String code,
        String category,
        String severity,
        ArchDoxEngineFindingSource source,
        String location,
        String message,
        List<String> legalReferences,
        Map<String, Object> metadata
) {
    public ArchDoxEngineFinding {
        legalReferences = legalReferences == null ? List.of() : List.copyOf(legalReferences);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

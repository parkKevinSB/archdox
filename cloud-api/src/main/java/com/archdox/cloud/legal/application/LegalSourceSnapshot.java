package com.archdox.cloud.legal.application;

import java.util.List;
import java.util.Map;

public record LegalSourceSnapshot(
        String sourceCode,
        String sourceType,
        String displayName,
        String baseUrl,
        Map<String, Object> metadata,
        List<LegalActSnapshot> acts
) {
    public LegalSourceSnapshot {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        acts = acts == null ? List.of() : List.copyOf(acts);
    }
}

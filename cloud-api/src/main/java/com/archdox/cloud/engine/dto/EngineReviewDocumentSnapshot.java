package com.archdox.cloud.engine.dto;

import java.util.List;
import java.util.Map;

public record EngineReviewDocumentSnapshot(
        String reviewSessionId,
        String customerProjectRef,
        String reviewPurpose,
        String documentTypeHint,
        String fileName,
        String documentText,
        List<Map<String, Object>> facts
) {
    public EngineReviewDocumentSnapshot {
        facts = facts == null ? List.of() : facts.stream().map(Map::copyOf).toList();
    }
}

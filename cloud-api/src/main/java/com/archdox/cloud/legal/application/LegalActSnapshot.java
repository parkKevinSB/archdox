package com.archdox.cloud.legal.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record LegalActSnapshot(
        String actCode,
        String actName,
        String actType,
        String jurisdiction,
        String sourceLawId,
        String sourceVersionKey,
        LocalDate promulgationDate,
        LocalDate effectiveDate,
        String sourceUrl,
        Map<String, Object> metadata,
        List<LegalArticleSnapshot> articles
) {
    public LegalActSnapshot {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        articles = articles == null ? List.of() : List.copyOf(articles);
    }
}

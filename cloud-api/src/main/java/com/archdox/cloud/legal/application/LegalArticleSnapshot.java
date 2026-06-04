package com.archdox.cloud.legal.application;

import java.util.Map;

public record LegalArticleSnapshot(
        String articleKey,
        String articleNo,
        String articleTitle,
        String parentArticleKey,
        int sortOrder,
        String articleText,
        Map<String, Object> metadata
) {
    public LegalArticleSnapshot {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

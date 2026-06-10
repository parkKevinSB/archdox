package com.archdox.documentai;

import java.util.List;
import java.util.Objects;

public record NarrativePolishResult(
        NarrativePolishStatus status,
        String summary,
        List<NarrativePolishSuggestion> suggestions
) {
    public NarrativePolishResult {
        Objects.requireNonNull(status, "status must not be null");
        summary = summary == null ? "" : summary.trim();
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        if (status == NarrativePolishStatus.NO_CHANGES && !suggestions.isEmpty()) {
            throw new IllegalArgumentException("NO_CHANGES result must not contain suggestions");
        }
    }
}

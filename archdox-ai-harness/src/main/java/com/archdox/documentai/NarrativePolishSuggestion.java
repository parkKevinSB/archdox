package com.archdox.documentai;

public record NarrativePolishSuggestion(
        String path,
        String label,
        String originalText,
        String polishedText,
        String reason,
        NarrativePolishConfidence confidence,
        boolean applicable
) {
    public NarrativePolishSuggestion {
        path = path == null ? "" : path.trim();
        label = label == null ? "" : label.trim();
        originalText = originalText == null ? "" : originalText.trim();
        polishedText = polishedText == null ? "" : polishedText.trim();
        reason = reason == null ? "" : reason.trim();
        confidence = confidence == null ? NarrativePolishConfidence.MEDIUM : confidence;
    }
}

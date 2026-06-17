package com.archdox.cloud.documentai.dto;

import java.util.List;

public record DocumentNarrativePolishResponse(
        String status,
        String summary,
        String providerCode,
        String modelId,
        String aiHarnessRunId,
        List<SuggestionResponse> suggestions
) {
    public record SuggestionResponse(
            String path,
            String label,
            String originalText,
            String polishedText,
            String reason,
            String source,
            String confidence,
            boolean applicable
    ) {
    }
}

package com.archdox.cloud.documentai.application;

import java.util.Map;

public record DeterministicDocumentReviewFinding(
        String code,
        String severity,
        String location,
        String message,
        String evidence,
        Map<String, String> attributes
) {
    public DeterministicDocumentReviewFinding {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}

package com.archdox.documentai;

import java.util.Map;

public record ReportPreflightFindingSummary(
        String source,
        String code,
        String severity,
        String location,
        String message,
        String evidence,
        Map<String, String> attributes
) {
    public ReportPreflightFindingSummary {
        source = blankToDefault(source, "DETERMINISTIC");
        code = blankToDefault(code, "UNKNOWN");
        severity = blankToDefault(severity, "LOW");
        location = location == null ? "" : location.trim();
        message = blankToDefault(message, "");
        evidence = evidence == null ? "" : evidence.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    private static String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}

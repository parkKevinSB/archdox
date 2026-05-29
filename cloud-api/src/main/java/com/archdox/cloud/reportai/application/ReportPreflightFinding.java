package com.archdox.cloud.reportai.application;

import java.util.Map;

public record ReportPreflightFinding(
        String source,
        String code,
        String severity,
        String location,
        String message,
        String evidence,
        Map<String, String> attributes
) {
    public ReportPreflightFinding {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}

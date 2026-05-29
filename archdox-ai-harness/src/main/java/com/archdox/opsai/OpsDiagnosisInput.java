package com.archdox.opsai;

import java.util.Map;

public record OpsDiagnosisInput(
        String opsRunId,
        String incidentId,
        String officeId,
        String category,
        String severity,
        Map<String, Object> redactedSnapshot
) {
    public OpsDiagnosisInput {
        opsRunId = blankToDefault(opsRunId, "");
        incidentId = blankToDefault(incidentId, "");
        officeId = blankToDefault(officeId, "");
        category = blankToDefault(category, "");
        severity = blankToDefault(severity, "");
        redactedSnapshot = redactedSnapshot == null ? Map.of() : Map.copyOf(redactedSnapshot);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}

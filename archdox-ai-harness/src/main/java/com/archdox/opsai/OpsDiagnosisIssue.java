package com.archdox.opsai;

public record OpsDiagnosisIssue(
        String code,
        String category,
        OpsDiagnosisIssueSeverity severity,
        String title,
        String message,
        String evidence,
        String likelyCause,
        String recommendation,
        String suggestedAction
) {
    public OpsDiagnosisIssue {
        code = blankToDefault(code, "OPS_DIAGNOSIS_FINDING");
        category = blankToDefault(category, "OPS_AI_DIAGNOSIS");
        severity = severity == null ? OpsDiagnosisIssueSeverity.INFO : severity;
        title = blankToDefault(title, code);
        message = blankToDefault(message, title);
        evidence = blankToDefault(evidence, "");
        likelyCause = blankToDefault(likelyCause, "");
        recommendation = blankToDefault(recommendation, "");
        suggestedAction = blankToDefault(suggestedAction, "");
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}

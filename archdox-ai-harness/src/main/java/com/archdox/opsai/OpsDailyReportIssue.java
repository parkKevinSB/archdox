package com.archdox.opsai;

public record OpsDailyReportIssue(
        String code,
        String category,
        OpsDailyReportIssueSeverity severity,
        String title,
        String message,
        String evidence,
        String likelyCause,
        String recommendation,
        String suggestedAction
) {
    public OpsDailyReportIssue {
        code = blankToDefault(code, "OPS_DAILY_REPORT_FINDING");
        category = blankToDefault(category, "OPS_DAILY_REPORT");
        severity = severity == null ? OpsDailyReportIssueSeverity.INFO : severity;
        title = blankToDefault(title, code);
        message = blankToDefault(message, title);
        evidence = blankToDefault(evidence, "");
        likelyCause = blankToDefault(likelyCause, "");
        recommendation = blankToDefault(recommendation, "");
        suggestedAction = blankToDefault(suggestedAction, "NONE");
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}

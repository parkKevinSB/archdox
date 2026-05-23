package com.archdox.cloud.inspection.dto;

import java.util.List;

public record ReportSubmitValidationResponse(
        boolean valid,
        String message,
        List<ReportSubmitValidationIssueResponse> blockingIssues,
        List<ReportSubmitValidationIssueResponse> warnings
) {
    public static ReportSubmitValidationResponse valid(List<ReportSubmitValidationIssueResponse> warnings) {
        return new ReportSubmitValidationResponse(true, "Report is ready to submit", List.of(), warnings);
    }

    public static ReportSubmitValidationResponse invalid(
            List<ReportSubmitValidationIssueResponse> blockingIssues,
            List<ReportSubmitValidationIssueResponse> warnings
    ) {
        return new ReportSubmitValidationResponse(false, "Report is not ready to submit", blockingIssues, warnings);
    }
}

package com.archdox.documentai;

import java.util.List;
import java.util.Map;

public record NarrativePolishInput(
        String officeId,
        String reportId,
        String reportType,
        String title,
        String outputPurpose,
        List<NarrativePolishField> fields,
        Map<String, Object> reportContext
) {
    public NarrativePolishInput {
        officeId = officeId == null ? "" : officeId.trim();
        reportId = reportId == null ? "" : reportId.trim();
        reportType = reportType == null ? "" : reportType.trim();
        title = title == null ? "" : title.trim();
        outputPurpose = outputPurpose == null || outputPurpose.isBlank() ? "DOCUMENT_RENDER_DRAFT" : outputPurpose.trim();
        fields = fields == null ? List.of() : List.copyOf(fields);
        reportContext = reportContext == null ? Map.of() : Map.copyOf(reportContext);
    }
}

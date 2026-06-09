package com.archdox.cloud.reportai.dto;

public record ReportPreflightLegalReferenceResponse(
        String referenceId,
        String label,
        String resolutionSource,
        String bindingScope,
        String bindingKey,
        String relevance,
        String catalogCode,
        Integer catalogVersion,
        String checklistItemCode
) {
}

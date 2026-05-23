package com.archdox.cloud.configuration.application;

public record ResolvedDocumentConfiguration(
        Long officeId,
        String reportType,
        ResolvedDocumentTemplateConfig template,
        ResolvedDocumentConfigPart workflow,
        ResolvedDocumentConfigPart ruleSet,
        ResolvedDocumentConfigPart outputLayout
) {
}

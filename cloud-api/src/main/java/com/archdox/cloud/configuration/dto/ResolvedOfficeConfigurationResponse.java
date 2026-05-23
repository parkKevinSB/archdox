package com.archdox.cloud.configuration.dto;

public record ResolvedOfficeConfigurationResponse(
        Long officeId,
        String reportType,
        ResolvedConfigPartResponse template,
        ResolvedConfigPartResponse workflow,
        ResolvedConfigPartResponse ruleSet,
        ResolvedConfigPartResponse outputLayout
) {
}

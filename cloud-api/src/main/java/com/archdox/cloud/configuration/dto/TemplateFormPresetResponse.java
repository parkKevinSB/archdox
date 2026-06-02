package com.archdox.cloud.configuration.dto;

import java.util.List;

public record TemplateFormPresetResponse(
        String code,
        String title,
        String description,
        String templateKind,
        String customizationPolicy,
        String renderingPolicy,
        List<String> reportTypes,
        List<String> recommendedFields,
        List<String> layoutSections
) {
}

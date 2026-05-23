package com.archdox.cloud.configuration.dto;

import java.util.List;

public record TemplateFormPresetResponse(
        String code,
        String title,
        String description,
        List<String> reportTypes,
        List<String> recommendedFields,
        List<String> layoutSections
) {
}

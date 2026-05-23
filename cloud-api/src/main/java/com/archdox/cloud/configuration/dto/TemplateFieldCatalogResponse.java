package com.archdox.cloud.configuration.dto;

import java.util.List;

public record TemplateFieldCatalogResponse(
        String reportType,
        List<TemplateFieldResponse> fields,
        List<TemplateFormPresetResponse> presets
) {
}

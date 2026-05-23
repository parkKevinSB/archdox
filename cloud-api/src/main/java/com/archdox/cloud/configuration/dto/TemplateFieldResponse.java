package com.archdox.cloud.configuration.dto;

import java.util.List;

public record TemplateFieldResponse(
        String key,
        String label,
        String category,
        String source,
        String example,
        String description,
        List<String> reportTypes
) {
}

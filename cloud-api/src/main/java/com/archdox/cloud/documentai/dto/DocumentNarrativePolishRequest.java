package com.archdox.cloud.documentai.dto;

import java.util.List;

public record DocumentNarrativePolishRequest(
        List<FieldRequest> fields
) {
    public record FieldRequest(
            String path,
            String label,
            String value
    ) {
    }
}

package com.archdox.cloud.configuration.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateDocumentTemplateRevisionRequest(
        @Size(max = 50) String templateStorageKind,
        @Size(max = 1000) String templateStorageRef,
        Map<String, Object> schema,
        Map<String, Object> composePolicy,
        Map<String, Object> aiPrompts
) {
}

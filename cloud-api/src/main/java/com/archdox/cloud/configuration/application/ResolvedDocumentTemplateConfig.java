package com.archdox.cloud.configuration.application;

import com.archdox.cloud.configuration.domain.ConfigResolutionSource;
import java.util.Map;

public record ResolvedDocumentTemplateConfig(
        ConfigResolutionSource source,
        Long definitionId,
        Long revisionId,
        String code,
        String name,
        String reportType,
        Integer version,
        String templateStorageKind,
        String templateStorageRef,
        Map<String, Object> schema,
        Map<String, Object> composePolicy,
        Map<String, Object> aiPrompts
) {
    public static ResolvedDocumentTemplateConfig notConfigured() {
        return new ResolvedDocumentTemplateConfig(
                ConfigResolutionSource.NOT_CONFIGURED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                Map.of(),
                Map.of());
    }
}

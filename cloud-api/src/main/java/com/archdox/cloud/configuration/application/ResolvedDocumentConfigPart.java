package com.archdox.cloud.configuration.application;

import com.archdox.cloud.configuration.domain.ConfigResolutionSource;
import java.util.Map;

public record ResolvedDocumentConfigPart(
        ConfigResolutionSource source,
        Long definitionId,
        Long revisionId,
        String code,
        String name,
        String reportType,
        Integer version,
        Map<String, Object> payload
) {
    public static ResolvedDocumentConfigPart notConfigured() {
        return new ResolvedDocumentConfigPart(
                ConfigResolutionSource.NOT_CONFIGURED,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of());
    }
}

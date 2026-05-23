package com.archdox.cloud.configuration.dto;

import com.archdox.cloud.configuration.domain.ConfigResolutionSource;

public record ResolvedConfigPartResponse(
        ConfigResolutionSource source,
        Long definitionId,
        Long revisionId,
        String code,
        String name,
        String reportType,
        Integer version
) {
    public static ResolvedConfigPartResponse notConfigured() {
        return new ResolvedConfigPartResponse(
                ConfigResolutionSource.NOT_CONFIGURED,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}

package com.archdox.cloud.legal.dto;

import java.util.List;

public record LegalOpenApiStatusResponse(
        boolean enabled,
        boolean ocConfigured,
        String sourceCode,
        String baseUrl,
        String userAgent,
        long requestTimeoutMs,
        long requestIntervalMs,
        int maxAttempts,
        List<TargetResponse> targets
) {
    public LegalOpenApiStatusResponse {
        sourceCode = sourceCode == null ? "" : sourceCode.trim();
        baseUrl = baseUrl == null ? "" : baseUrl.trim();
        userAgent = userAgent == null ? "" : userAgent.trim();
        targets = targets == null ? List.of() : List.copyOf(targets);
    }

    public record TargetResponse(
            String target,
            String query,
            String expectedName,
            String actCode,
            String actType
    ) {
        public TargetResponse {
            target = target == null ? "" : target.trim();
            query = query == null ? "" : query.trim();
            expectedName = expectedName == null ? "" : expectedName.trim();
            actCode = actCode == null ? "" : actCode.trim();
            actType = actType == null ? "" : actType.trim();
        }
    }
}

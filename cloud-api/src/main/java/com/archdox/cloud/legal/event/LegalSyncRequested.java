package com.archdox.cloud.legal.event;

public record LegalSyncRequested(
        Long syncRunId,
        String sourceCode
) {
}

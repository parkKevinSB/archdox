package com.archdox.cloud.legal.dto;

public record LegalDigestRefreshResponse(
        int inspectedChangeSets,
        int createdDigests,
        int refreshedDigests,
        int skippedAiDigests,
        int skippedMissingActs
) {
}

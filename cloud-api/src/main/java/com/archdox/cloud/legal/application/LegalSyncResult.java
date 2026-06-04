package com.archdox.cloud.legal.application;

import java.util.Map;

public record LegalSyncResult(
        Long runId,
        int actsSeen,
        int versionsCreated,
        int changeSetsCreated,
        int articleDiffsCreated
) {
    public Map<String, Object> toSummaryJson() {
        return Map.of(
                "actsSeen", actsSeen,
                "versionsCreated", versionsCreated,
                "changeSetsCreated", changeSetsCreated,
                "articleDiffsCreated", articleDiffsCreated);
    }
}

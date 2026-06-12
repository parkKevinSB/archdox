package com.archdox.cloud.legal.application;

import java.util.LinkedHashMap;
import java.util.Map;

public record LegalSyncResult(
        Long runId,
        int actsSeen,
        int versionsCreated,
        int changeSetsCreated,
        int articleDiffsCreated,
        Map<String, Object> sourceMetadata
) {
    public LegalSyncResult(
            Long runId,
            int actsSeen,
            int versionsCreated,
            int changeSetsCreated,
            int articleDiffsCreated
    ) {
        this(runId, actsSeen, versionsCreated, changeSetsCreated, articleDiffsCreated, Map.of());
    }

    public LegalSyncResult {
        sourceMetadata = sourceMetadata == null ? Map.of() : Map.copyOf(sourceMetadata);
    }

    public Map<String, Object> toSummaryJson() {
        var summary = new LinkedHashMap<String, Object>();
        summary.put("actsSeen", actsSeen);
        summary.put("versionsCreated", versionsCreated);
        summary.put("changeSetsCreated", changeSetsCreated);
        summary.put("articleDiffsCreated", articleDiffsCreated);
        summary.putAll(sourceMetadata);
        return Map.copyOf(summary);
    }
}

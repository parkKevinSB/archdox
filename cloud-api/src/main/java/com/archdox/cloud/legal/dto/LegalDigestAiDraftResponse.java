package com.archdox.cloud.legal.dto;

import java.util.List;
import java.util.UUID;

public record LegalDigestAiDraftResponse(
        UUID workerRequestId,
        Long digestId,
        Long changeSetId,
        boolean dryRun,
        String workerStatus,
        String resultCode,
        String aiHarnessRunId,
        String digestDraftStatus,
        String title,
        String summary,
        String impactSummary,
        String confidence,
        List<String> affectedReportTypes,
        List<String> affectedCatalogItems,
        List<String> keyArticles,
        String reviewNotes,
        boolean publicationApplied,
        boolean corpusMutated,
        boolean digestMutated
) {
    public LegalDigestAiDraftResponse {
        affectedReportTypes = affectedReportTypes == null ? List.of() : List.copyOf(affectedReportTypes);
        affectedCatalogItems = affectedCatalogItems == null ? List.of() : List.copyOf(affectedCatalogItems);
        keyArticles = keyArticles == null ? List.of() : List.copyOf(keyArticles);
    }
}

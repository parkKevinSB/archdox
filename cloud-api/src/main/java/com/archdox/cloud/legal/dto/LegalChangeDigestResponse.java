package com.archdox.cloud.legal.dto;

import com.archdox.cloud.legal.domain.LegalChangeDigestSource;
import com.archdox.cloud.legal.domain.LegalChangeDigestStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record LegalChangeDigestResponse(
        Long id,
        Long changeSetId,
        LegalChangeDigestStatus status,
        LegalChangeDigestSource source,
        String title,
        String summary,
        String impactSummary,
        List<String> affectedReportTypes,
        List<String> affectedCatalogItems,
        String aiHarnessRunId,
        LocalDate effectiveDate,
        OffsetDateTime detectedAt,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

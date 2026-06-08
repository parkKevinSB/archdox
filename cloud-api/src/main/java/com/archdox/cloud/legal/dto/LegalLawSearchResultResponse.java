package com.archdox.cloud.legal.dto;

import java.time.LocalDate;

public record LegalLawSearchResultResponse(
        String referenceId,
        String evidenceType,
        String sourceCode,
        Long actId,
        String actCode,
        String actName,
        String actType,
        Long legalVersionId,
        String sourceVersionKey,
        LocalDate effectiveDate,
        String sourceUrl,
        String publicSourceUrl,
        Long articleId,
        Long articleVersionId,
        String articleKey,
        String articleNo,
        String articleTitle,
        String snippet,
        String contentHash
) {
}

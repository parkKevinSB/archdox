package com.archdox.cloud.legal.dto;

import java.time.LocalDate;

public record LegalLawArticleResponse(
        String sourceCode,
        Long actId,
        String actCode,
        String actName,
        String actType,
        Long legalVersionId,
        String sourceVersionKey,
        LocalDate effectiveDate,
        String sourceUrl,
        Long articleId,
        Long articleVersionId,
        String articleKey,
        String articleNo,
        String articleTitle,
        String articleText,
        String contentHash
) {
}

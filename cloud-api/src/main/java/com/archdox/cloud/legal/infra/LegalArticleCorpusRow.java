package com.archdox.cloud.legal.infra;

import java.time.LocalDate;

public record LegalArticleCorpusRow(
        Long actId,
        String actCode,
        String actName,
        String actType,
        String sourceCode,
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

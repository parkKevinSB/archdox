package com.archdox.cloud.legal.dto;

import com.archdox.cloud.legal.domain.LegalArticleChangeType;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record LegalArticleDiffResponse(
        Long id,
        Long articleId,
        String articleKey,
        String articleNo,
        String articleTitle,
        LegalArticleChangeType changeType,
        Long beforeArticleVersionId,
        Long afterArticleVersionId,
        String beforeHash,
        String afterHash,
        String beforeTextPreview,
        String afterTextPreview,
        Long legalVersionId,
        String sourceVersionKey,
        LocalDate effectiveDate,
        String sourceUrl,
        String diffSummary,
        OffsetDateTime createdAt
) {
}

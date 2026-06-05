package com.archdox.cloud.legal.dto;

import com.archdox.cloud.legal.domain.LegalArticleChangeType;
import java.time.OffsetDateTime;

public record LegalArticleDiffResponse(
        Long id,
        Long articleId,
        String articleKey,
        String articleNo,
        LegalArticleChangeType changeType,
        Long beforeArticleVersionId,
        Long afterArticleVersionId,
        String beforeHash,
        String afterHash,
        String diffSummary,
        OffsetDateTime createdAt
) {
}

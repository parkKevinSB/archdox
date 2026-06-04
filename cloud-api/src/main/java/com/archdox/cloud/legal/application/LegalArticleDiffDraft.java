package com.archdox.cloud.legal.application;

import com.archdox.cloud.legal.domain.LegalArticleChangeType;

public record LegalArticleDiffDraft(
        LegalArticleChangeType changeType,
        Long articleId,
        String articleKey,
        String articleNo,
        Long beforeArticleVersionId,
        Long afterArticleVersionId,
        String beforeHash,
        String afterHash,
        String summary
) {
}

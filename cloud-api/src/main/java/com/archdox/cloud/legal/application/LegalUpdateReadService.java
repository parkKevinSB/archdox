package com.archdox.cloud.legal.application;

import com.archdox.cloud.legal.domain.LegalChangeDigestStatus;
import com.archdox.cloud.legal.dto.LegalArticleDiffResponse;
import com.archdox.cloud.legal.dto.LegalChangeDigestResponse;
import com.archdox.cloud.legal.infra.LegalArticleDiffRepository;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegalUpdateReadService {
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 365;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final LegalChangeDigestRepository repository;
    private final LegalArticleDiffRepository articleDiffRepository;

    public LegalUpdateReadService(
            LegalChangeDigestRepository repository,
            LegalArticleDiffRepository articleDiffRepository
    ) {
        this.repository = repository;
        this.articleDiffRepository = articleDiffRepository;
    }

    @Transactional(readOnly = true)
    public List<LegalChangeDigestResponse> recent(Integer days, Integer limit) {
        var publishedAfter = OffsetDateTime.now().minusDays(clampedDays(days));
        return repository.findPublishedExcludingSourceCode(
                        LegalChangeDigestStatus.PUBLISHED,
                        publishedAfter,
                        FakeLegalSourceClient.DEFAULT_SOURCE_CODE,
                        PageRequest.of(0, clampedLimit(limit)))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LegalChangeDigestResponse detail(Long id) {
        return repository.findPublishedByIdExcludingSourceCode(
                        id,
                        LegalChangeDigestStatus.PUBLISHED,
                        FakeLegalSourceClient.DEFAULT_SOURCE_CODE)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Legal update not found: " + id));
    }

    public LegalChangeDigestResponse toResponse(com.archdox.cloud.legal.domain.LegalChangeDigest digest) {
        return new LegalChangeDigestResponse(
                digest.id(),
                digest.changeSetId(),
                digest.status(),
                digest.source(),
                digest.title(),
                digest.summary(),
                digest.impactSummary(),
                digest.affectedReportTypes(),
                digest.affectedCatalogItems(),
                digest.aiHarnessRunId(),
                digest.effectiveDate(),
                digest.detectedAt(),
                digest.publishedAt(),
                digest.createdAt(),
                digest.updatedAt(),
                articleDiffRepository.findByChangeSetIdOrderByIdAsc(digest.changeSetId())
                        .stream()
                        .map(diff -> new LegalArticleDiffResponse(
                                diff.id(),
                                diff.articleId(),
                                diff.articleKey(),
                                diff.articleNo(),
                                diff.changeType(),
                                diff.beforeArticleVersionId(),
                                diff.afterArticleVersionId(),
                                diff.beforeHash(),
                                diff.afterHash(),
                                diff.diffSummary(),
                                diff.createdAt()))
                        .toList());
    }

    private int clampedDays(Integer days) {
        return Math.max(1, Math.min(days == null ? DEFAULT_DAYS : days, MAX_DAYS));
    }

    private int clampedLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }
}

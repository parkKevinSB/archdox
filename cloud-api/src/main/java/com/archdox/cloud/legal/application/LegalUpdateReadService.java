package com.archdox.cloud.legal.application;

import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalChangeDigestStatus;
import com.archdox.cloud.legal.domain.LegalArticleVersion;
import com.archdox.cloud.legal.domain.LegalVersion;
import com.archdox.cloud.legal.dto.LegalArticleDiffResponse;
import com.archdox.cloud.legal.dto.LegalChangeDigestResponse;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleDiffRepository;
import com.archdox.cloud.legal.infra.LegalArticleVersionRepository;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import com.archdox.cloud.legal.infra.LegalVersionRepository;
import com.archdox.cloud.global.api.NotFoundException;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegalUpdateReadService {
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 365;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int TEXT_PREVIEW_LIMIT = 4000;

    private final LegalChangeDigestRepository repository;
    private final LegalArticleDiffRepository articleDiffRepository;
    private final LegalArticleVersionRepository articleVersionRepository;
    private final LegalVersionRepository versionRepository;
    private final LegalActRepository actRepository;
    private final LegalPublicSourceUrlFactory publicSourceUrlFactory;

    public LegalUpdateReadService(
            LegalChangeDigestRepository repository,
            LegalArticleDiffRepository articleDiffRepository,
            LegalArticleVersionRepository articleVersionRepository,
            LegalVersionRepository versionRepository,
            LegalActRepository actRepository,
            LegalPublicSourceUrlFactory publicSourceUrlFactory
    ) {
        this.repository = repository;
        this.articleDiffRepository = articleDiffRepository;
        this.articleVersionRepository = articleVersionRepository;
        this.versionRepository = versionRepository;
        this.actRepository = actRepository;
        this.publicSourceUrlFactory = publicSourceUrlFactory;
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
                .orElseThrow(() -> new NotFoundException(
                        "LEGAL_UPDATE_NOT_FOUND",
                        "errors.legalUpdate.notFound",
                        "Legal update not found",
                        Map.of("digestId", id)));
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
                articleDiffResponses(digest.changeSetId()));
    }

    private List<LegalArticleDiffResponse> articleDiffResponses(Long changeSetId) {
        var diffs = articleDiffRepository.findByChangeSetIdOrderByIdAsc(changeSetId);
        var articleVersionIds = new LinkedHashSet<Long>();
        diffs.forEach(diff -> {
            if (diff.beforeArticleVersionId() != null) {
                articleVersionIds.add(diff.beforeArticleVersionId());
            }
            if (diff.afterArticleVersionId() != null) {
                articleVersionIds.add(diff.afterArticleVersionId());
            }
        });
        var articleVersions = articleVersionRepository.findAllById(articleVersionIds)
                .stream()
                .collect(Collectors.toMap(LegalArticleVersion::id, Function.identity()));
        var legalVersionIds = articleVersions.values().stream()
                .map(LegalArticleVersion::legalVersionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var legalVersions = versionRepository.findAllById(legalVersionIds)
                .stream()
                .collect(Collectors.toMap(LegalVersion::id, Function.identity()));
        var actIds = legalVersions.values().stream()
                .map(LegalVersion::actId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var acts = actRepository.findAllById(actIds)
                .stream()
                .collect(Collectors.toMap(LegalAct::id, Function.identity()));

        return diffs.stream()
                .map(diff -> articleDiffResponse(diff, articleVersions, legalVersions, acts))
                .toList();
    }

    private LegalArticleDiffResponse articleDiffResponse(
            com.archdox.cloud.legal.domain.LegalArticleDiff diff,
            Map<Long, LegalArticleVersion> articleVersions,
            Map<Long, LegalVersion> legalVersions,
            Map<Long, LegalAct> acts
    ) {
        var before = diff.beforeArticleVersionId() == null ? null : articleVersions.get(diff.beforeArticleVersionId());
        var after = diff.afterArticleVersionId() == null ? null : articleVersions.get(diff.afterArticleVersionId());
        var display = after == null ? before : after;
        var legalVersion = display == null ? null : legalVersions.get(display.legalVersionId());
        var act = legalVersion == null ? null : acts.get(legalVersion.actId());
        return new LegalArticleDiffResponse(
                diff.id(),
                diff.articleId(),
                diff.articleKey(),
                diff.articleNo(),
                display == null ? "" : text(display.articleTitle()),
                diff.changeType(),
                diff.beforeArticleVersionId(),
                diff.afterArticleVersionId(),
                diff.beforeHash(),
                diff.afterHash(),
                preview(before == null ? "" : before.articleText()),
                preview(after == null ? "" : after.articleText()),
                legalVersion == null ? null : legalVersion.id(),
                legalVersion == null ? "" : text(legalVersion.sourceVersionKey()),
                display == null ? null : display.effectiveDate(),
                legalVersion == null ? "" : text(legalVersion.sourceUrl()),
                publicSourceUrlFactory.publicSourceUrl(
                        legalVersion == null ? "" : legalVersion.sourceUrl(),
                        act == null ? "" : act.actType(),
                        act == null ? "" : act.actName()),
                diff.diffSummary(),
                diff.createdAt());
    }

    private String preview(String value) {
        var text = text(value)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t ]+\\n", "\n")
                .replaceAll("\\n[\\t ]+", "\n")
                .replaceAll("\\n{4,}", "\n\n\n");
        if (text.length() <= TEXT_PREVIEW_LIMIT) {
            return text;
        }
        return text.substring(0, TEXT_PREVIEW_LIMIT).trim() + "\n\n... 본문이 길어 일부만 표시합니다. 전체 내용은 법령정보센터에서 확인하세요.";
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private int clampedDays(Integer days) {
        return Math.max(1, Math.min(days == null ? DEFAULT_DAYS : days, MAX_DAYS));
    }

    private int clampedLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }
}

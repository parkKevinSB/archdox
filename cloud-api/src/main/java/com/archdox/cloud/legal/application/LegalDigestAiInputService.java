package com.archdox.cloud.legal.application;

import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalArticleDiff;
import com.archdox.cloud.legal.domain.LegalChangeSet;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleCorpusRow;
import com.archdox.cloud.legal.infra.LegalArticleDiffRepository;
import com.archdox.cloud.legal.infra.LegalArticleVersionRepository;
import com.archdox.cloud.legal.infra.LegalChangeSetRepository;
import com.archdox.legalai.LegalDigestArticleChange;
import com.archdox.legalai.LegalDigestInput;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegalDigestAiInputService {
    private static final int ARTICLE_CHANGE_LIMIT = 40;

    private final LegalChangeSetRepository changeSetRepository;
    private final LegalActRepository actRepository;
    private final LegalArticleDiffRepository articleDiffRepository;
    private final LegalArticleVersionRepository articleVersionRepository;

    public LegalDigestAiInputService(
            LegalChangeSetRepository changeSetRepository,
            LegalActRepository actRepository,
            LegalArticleDiffRepository articleDiffRepository,
            LegalArticleVersionRepository articleVersionRepository
    ) {
        this.changeSetRepository = changeSetRepository;
        this.actRepository = actRepository;
        this.articleDiffRepository = articleDiffRepository;
        this.articleVersionRepository = articleVersionRepository;
    }

    @Transactional(readOnly = true)
    public LegalDigestInput buildInput(Long changeSetId) {
        var changeSet = changeSetRepository.findById(changeSetId)
                .orElseThrow(() -> new NotFoundException("Legal change set not found"));
        var act = actRepository.findById(changeSet.actId())
                .orElseThrow(() -> new NotFoundException("Legal act not found"));
        var diffs = articleDiffRepository.findByChangeSetIdOrderByIdAsc(changeSet.id());
        return buildInput(changeSet, act, diffs);
    }

    public LegalDigestInput buildInput(LegalChangeSet changeSet, LegalAct act, List<LegalArticleDiff> diffs) {
        var safeDiffs = diffs == null ? List.<LegalArticleDiff>of() : diffs;
        var rowsByVersionId = new HashMap<Long, Optional<LegalArticleCorpusRow>>();
        var articleChanges = safeDiffs.stream()
                .limit(ARTICLE_CHANGE_LIMIT)
                .map(diff -> articleChange(diff, rowsByVersionId))
                .toList();
        var sourceCode = rowsByVersionId.values().stream()
                .flatMap(Optional::stream)
                .map(LegalArticleCorpusRow::sourceCode)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("LEGAL_CORPUS");
        return new LegalDigestInput(
                String.valueOf(changeSet.id()),
                act.actCode(),
                act.actName(),
                act.actType(),
                sourceCode,
                stringValue(changeSet.effectiveDate()),
                stringValue(changeSet.detectedAt()),
                changeSet.summary(),
                articleChanges);
    }

    private LegalDigestArticleChange articleChange(
            LegalArticleDiff diff,
            Map<Long, Optional<LegalArticleCorpusRow>> rowsByVersionId
    ) {
        var before = corpusRow(diff.beforeArticleVersionId(), rowsByVersionId).orElse(null);
        var after = corpusRow(diff.afterArticleVersionId(), rowsByVersionId).orElse(null);
        return new LegalDigestArticleChange(
                diff.articleKey(),
                articleTitle(diff, before, after),
                diff.changeType().name(),
                before == null ? "" : before.articleText(),
                after == null ? "" : after.articleText(),
                firstNonBlank(
                        after == null ? null : after.sourceVersionKey(),
                        before == null ? null : before.sourceVersionKey()),
                firstNonBlank(
                        stringValue(after == null ? null : after.effectiveDate()),
                        stringValue(before == null ? null : before.effectiveDate())),
                firstNonBlank(
                        after == null ? null : after.sourceUrl(),
                        before == null ? null : before.sourceUrl()));
    }

    private Optional<LegalArticleCorpusRow> corpusRow(
            Long articleVersionId,
            Map<Long, Optional<LegalArticleCorpusRow>> rowsByVersionId
    ) {
        if (articleVersionId == null) {
            return Optional.empty();
        }
        return rowsByVersionId.computeIfAbsent(articleVersionId,
                id -> articleVersionRepository.findCorpusRowByArticleVersionId(id, FakeLegalSourceClient.DEFAULT_SOURCE_CODE));
    }

    private String articleTitle(LegalArticleDiff diff, LegalArticleCorpusRow before, LegalArticleCorpusRow after) {
        var articleNo = firstNonBlank(
                diff.articleNo(),
                after == null ? null : after.articleNo(),
                before == null ? null : before.articleNo());
        var title = firstNonBlank(
                after == null ? null : after.articleTitle(),
                before == null ? null : before.articleTitle());
        if (articleNo == null) {
            return title == null ? "" : title;
        }
        if (title == null) {
            return articleNo;
        }
        return articleNo + " " + title;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String stringValue(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private String stringValue(OffsetDateTime value) {
        return value == null ? "" : value.toString();
    }
}

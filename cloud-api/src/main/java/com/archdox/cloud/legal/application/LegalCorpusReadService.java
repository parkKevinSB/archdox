package com.archdox.cloud.legal.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.legal.dto.LegalLawArticleResponse;
import com.archdox.cloud.legal.dto.LegalLawSearchResponse;
import com.archdox.cloud.legal.dto.LegalLawSearchResultResponse;
import com.archdox.cloud.legal.infra.LegalArticleCorpusRow;
import com.archdox.cloud.legal.infra.LegalArticleVersionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegalCorpusReadService {
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_SNIPPET_LENGTH = 240;
    private static final int SNIPPET_CONTEXT_LENGTH = 80;
    private static final String EVIDENCE_TYPE_LEGAL_ARTICLE = "LEGAL_ARTICLE";

    private final LegalArticleVersionRepository articleVersionRepository;
    private final LegalPublicSourceUrlFactory publicSourceUrlFactory;

    public LegalCorpusReadService(
            LegalArticleVersionRepository articleVersionRepository,
            LegalPublicSourceUrlFactory publicSourceUrlFactory
    ) {
        this.articleVersionRepository = articleVersionRepository;
        this.publicSourceUrlFactory = publicSourceUrlFactory;
    }

    @Transactional(readOnly = true)
    public LegalLawSearchResponse search(
            String query,
            String actCode,
            String actName,
            String articleNo,
            LocalDate effectiveDate,
            Integer limit
    ) {
        var normalizedQuery = blankToNull(query);
        var normalizedActCode = upperBlankToNull(actCode);
        var normalizedActName = blankToNull(actName);
        var normalizedArticleNo = normalizeArticleNo(articleNo);
        requireSearchSelector(normalizedQuery, normalizedActCode, normalizedActName, normalizedArticleNo);
        var effectiveLimit = limit(limit);
        var rows = articleVersionRepository.searchLatestArticles(
                valueOrEmpty(normalizedQuery),
                normalizedQuery != null,
                valueOrEmpty(normalizedActCode),
                normalizedActCode != null,
                valueOrEmpty(normalizedActName),
                normalizedActName != null,
                valueOrEmpty(normalizedArticleNo),
                normalizedArticleNo != null,
                effectiveDate,
                FakeLegalSourceClient.DEFAULT_SOURCE_CODE,
                PageRequest.of(0, effectiveLimit));
        var items = rows.stream()
                .map(row -> toSearchResult(row, normalizedQuery))
                .toList();
        return new LegalLawSearchResponse(
                items,
                items.size(),
                normalizedQuery,
                normalizedActCode,
                normalizedActName,
                normalizedArticleNo,
                effectiveDate,
                effectiveLimit);
    }

    @Transactional(readOnly = true)
    public LegalLawArticleResponse getArticle(
            Long articleVersionId,
            Long articleId,
            String actCode,
            String articleNo,
            LocalDate effectiveDate
    ) {
        if (articleVersionId != null) {
            return articleVersionRepository.findCorpusRowByArticleVersionId(
                            articleVersionId,
                            FakeLegalSourceClient.DEFAULT_SOURCE_CODE)
                    .map(this::toArticleResponse)
                    .orElseThrow(() -> articleNotFound(Map.of("articleVersionId", articleVersionId)));
        }
        if (articleId != null) {
            return first(articleVersionRepository.findLatestCorpusRowsByArticleId(
                    articleId,
                    effectiveDate,
                    FakeLegalSourceClient.DEFAULT_SOURCE_CODE,
                    PageRequest.of(0, 1))).map(this::toArticleResponse)
                    .orElseThrow(() -> articleNotFound(Map.of("articleId", articleId)));
        }
        var normalizedActCode = upperBlankToNull(actCode);
        var normalizedArticleNo = normalizeArticleNo(articleNo);
        if (normalizedActCode == null || normalizedArticleNo == null) {
            throw new BadRequestException(
                    "LEGAL_ARTICLE_SELECTOR_REQUIRED",
                    "errors.legal.article.selectorRequired",
                    "articleVersionId, articleId, or actCode and articleNo are required");
        }
        return first(articleVersionRepository.findLatestCorpusRowsByActCodeAndArticleNo(
                normalizedActCode,
                normalizedArticleNo,
                effectiveDate,
                FakeLegalSourceClient.DEFAULT_SOURCE_CODE,
                PageRequest.of(0, 1))).map(this::toArticleResponse)
                .orElseThrow(() -> articleNotFound(Map.of(
                        "actCode", normalizedActCode,
                        "articleNo", normalizedArticleNo)));
    }

    private void requireSearchSelector(String query, String actCode, String actName, String articleNo) {
        if (query != null || actCode != null || actName != null || articleNo != null) {
            return;
        }
        throw new BadRequestException(
                "LEGAL_SEARCH_SELECTOR_REQUIRED",
                "errors.legal.search.selectorRequired",
                "query, actCode, actName, or articleNo is required");
    }

    private LegalLawSearchResultResponse toSearchResult(LegalArticleCorpusRow row, String query) {
        return new LegalLawSearchResultResponse(
                referenceId(row),
                EVIDENCE_TYPE_LEGAL_ARTICLE,
                row.sourceCode(),
                row.actId(),
                row.actCode(),
                row.actName(),
                row.actType(),
                row.legalVersionId(),
                row.sourceVersionKey(),
                row.effectiveDate(),
                row.sourceUrl(),
                publicSourceUrlFactory.publicSourceUrl(row.sourceUrl(), row.actType(), row.actName()),
                row.articleId(),
                row.articleVersionId(),
                row.articleKey(),
                row.articleNo(),
                row.articleTitle(),
                snippet(row.articleText(), query),
                row.contentHash());
    }

    private LegalLawArticleResponse toArticleResponse(LegalArticleCorpusRow row) {
        return new LegalLawArticleResponse(
                referenceId(row),
                EVIDENCE_TYPE_LEGAL_ARTICLE,
                row.sourceCode(),
                row.actId(),
                row.actCode(),
                row.actName(),
                row.actType(),
                row.legalVersionId(),
                row.sourceVersionKey(),
                row.effectiveDate(),
                row.sourceUrl(),
                publicSourceUrlFactory.publicSourceUrl(row.sourceUrl(), row.actType(), row.actName()),
                row.articleId(),
                row.articleVersionId(),
                row.articleKey(),
                row.articleNo(),
                row.articleTitle(),
                row.articleText(),
                row.contentHash());
    }

    private String referenceId(LegalArticleCorpusRow row) {
        var actCode = blankToNull(row.actCode());
        var articleKey = firstNonBlank(row.articleKey(), row.articleNo());
        var sourceVersionKey = blankToNull(row.sourceVersionKey());
        if (actCode == null || articleKey == null || sourceVersionKey == null) {
            return "";
        }
        return actCode + ":" + articleKey + "@" + sourceVersionKey;
    }

    private NotFoundException articleNotFound(Map<String, Object> params) {
        return new NotFoundException(
                "LEGAL_ARTICLE_NOT_FOUND",
                "errors.legal.article.notFound",
                "Legal article was not found",
                params);
    }

    private Optional<LegalArticleCorpusRow> first(List<LegalArticleCorpusRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    private int limit(Integer value) {
        if (value == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, value));
    }

    private String snippet(String value, String query) {
        var text = compact(value);
        if (text == null) {
            return "";
        }
        var normalizedQuery = blankToNull(query);
        if (normalizedQuery == null) {
            return truncate(text, MAX_SNIPPET_LENGTH);
        }
        var lowerText = text.toLowerCase(Locale.ROOT);
        var lowerQuery = normalizedQuery.toLowerCase(Locale.ROOT);
        var index = lowerText.indexOf(lowerQuery);
        if (index < 0) {
            return truncate(text, MAX_SNIPPET_LENGTH);
        }
        var start = Math.max(0, index - SNIPPET_CONTEXT_LENGTH);
        var end = Math.min(text.length(), index + normalizedQuery.length() + SNIPPET_CONTEXT_LENGTH);
        var snippet = text.substring(start, end).trim();
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < text.length()) {
            snippet = snippet + "...";
        }
        return truncate(snippet, MAX_SNIPPET_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private String normalizeArticleNo(String value) {
        var text = blankToNull(value);
        if (text == null) {
            return null;
        }
        if (text.startsWith("제")) {
            text = text.substring(1);
        }
        if (text.endsWith("조")) {
            text = text.substring(0, text.length() - 1);
        }
        text = text.replace("조의", "의");
        return blankToNull(text);
    }

    private String upperBlankToNull(String value) {
        var text = blankToNull(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private String compact(String value) {
        var text = blankToNull(value);
        return text == null ? null : text.replaceAll("\\s+", " ").trim();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        var normalizedFirst = blankToNull(first);
        return normalizedFirst == null ? blankToNull(second) : normalizedFirst;
    }
}

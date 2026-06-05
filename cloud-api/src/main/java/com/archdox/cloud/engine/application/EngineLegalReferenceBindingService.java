package com.archdox.cloud.engine.application;

import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalArticle;
import com.archdox.cloud.legal.domain.LegalArticleVersion;
import com.archdox.cloud.legal.domain.LegalDomainBinding;
import com.archdox.cloud.legal.domain.LegalVersion;
import com.archdox.cloud.legal.application.FakeLegalSourceClient;
import com.archdox.cloud.legal.infra.LegalArticleCorpusRow;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleRepository;
import com.archdox.cloud.legal.infra.LegalArticleVersionRepository;
import com.archdox.cloud.legal.infra.LegalDomainBindingRepository;
import com.archdox.cloud.legal.infra.LegalVersionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class EngineLegalReferenceBindingService {
    private static final String ACTIVE = "ACTIVE";
    private static final int CORPUS_REFERENCE_LIMIT_PER_QUERY = 3;
    private static final Set<String> CONSTRUCTION_SUPERVISION_REPORT_TYPES = Set.of(
            "CONSTRUCTION_DAILY_SUPERVISION_LOG",
            "CONSTRUCTION_SUPERVISION_REPORT");

    private final LegalDomainBindingRepository bindingRepository;
    private final LegalActRepository actRepository;
    private final LegalArticleRepository articleRepository;
    private final LegalVersionRepository versionRepository;
    private final LegalArticleVersionRepository articleVersionRepository;

    public EngineLegalReferenceBindingService(
            LegalDomainBindingRepository bindingRepository,
            LegalActRepository actRepository,
            LegalArticleRepository articleRepository,
            LegalVersionRepository versionRepository,
            LegalArticleVersionRepository articleVersionRepository
    ) {
        this.bindingRepository = bindingRepository;
        this.actRepository = actRepository;
        this.articleRepository = articleRepository;
        this.versionRepository = versionRepository;
        this.articleVersionRepository = articleVersionRepository;
    }

    public EngineLegalReferenceBindingResult resolve(
            List<Map<String, Object>> catalogBindings,
            String reportType,
            LocalDate effectiveDate
    ) {
        var bindings = new ArrayList<LegalDomainBinding>();
        for (var catalogBinding : catalogBindings == null ? List.<Map<String, Object>>of() : catalogBindings) {
            var catalogCode = text(catalogBinding.get("catalogCode"));
            var catalogVersion = integer(catalogBinding.get("catalogVersion"));
            var checklistItemCode = text(catalogBinding.get("inspectionItemCode"));
            if (!catalogCode.isBlank() && catalogVersion != null && !checklistItemCode.isBlank()) {
                bindings.addAll(nullToEmpty(bindingRepository
                        .findByStatusAndCatalogCodeAndCatalogVersionAndChecklistItemCodeOrderByIdAsc(
                                ACTIVE,
                                catalogCode,
                                catalogVersion,
                                checklistItemCode)));
            }
        }
        if (!isBlank(reportType)) {
            bindings.addAll(nullToEmpty(bindingRepository.findByStatusAndReportTypeOrderByIdAsc(
                    ACTIVE,
                    reportType.trim())));
        }

        var seen = new LinkedHashSet<Long>();
        var bindingReferences = bindings.stream()
                .filter(binding -> binding.id() != null && seen.add(binding.id()))
                .filter(binding -> effective(effectiveDate, binding.effectiveFrom(), binding.effectiveTo()))
                .map(this::reference)
                .filter(reference -> !reference.isEmpty())
                .toList();
        var corpusReferences = corpusReferences(
                catalogBindings == null ? List.of() : catalogBindings,
                reportType,
                effectiveDate,
                bindingReferences);
        var references = mergeReferences(bindingReferences, corpusReferences);

        return new EngineLegalReferenceBindingResult(
                references,
                Map.of(
                        "legalReferenceBindingApplied", !bindings.isEmpty(),
                        "legalReferenceCorpusSearchApplied", !corpusReferences.isEmpty(),
                        "legalReferenceBindingCount", bindingReferences.size(),
                        "legalReferenceCorpusCount", corpusReferences.size(),
                        "legalReferenceCount", references.size(),
                        "source", corpusReferences.isEmpty()
                                ? "LEGAL_DOMAIN_BINDINGS"
                                : "LEGAL_DOMAIN_BINDINGS_AND_CORPUS"));
    }

    private Map<String, Object> reference(LegalDomainBinding binding) {
        var act = actRepository.findById(binding.actId()).orElse(null);
        if (act == null) {
            return Map.of();
        }
        var article = binding.articleId() == null
                ? null
                : articleRepository.findById(binding.articleId()).orElse(null);
        var version = versionRepository.findFirstByActIdOrderByCapturedAtDescIdDesc(act.id()).orElse(null);
        var articleVersion = article == null || version == null
                ? null
                : articleVersionRepository.findByArticleIdAndLegalVersionId(article.id(), version.id()).orElse(null);

        var reference = new LinkedHashMap<String, Object>();
        reference.put("referenceId", referenceId(act, article, version));
        reference.put("actId", act.id());
        reference.put("actCode", act.actCode());
        reference.put("actName", act.actName());
        reference.put("actType", act.actType());
        reference.put("articleId", article == null ? "" : article.id());
        reference.put("articleKey", article == null ? "" : article.articleKey());
        reference.put("articleNo", articleNo(article, articleVersion));
        reference.put("articleTitle", articleTitle(article, articleVersion));
        reference.put("legalVersionId", version == null ? "" : version.id());
        reference.put("sourceVersionKey", version == null ? "" : version.sourceVersionKey());
        reference.put("effectiveDate", version == null || version.effectiveDate() == null ? "" : version.effectiveDate().toString());
        reference.put("bindingScope", binding.bindingScope());
        reference.put("bindingKey", binding.bindingKey());
        reference.put("relevance", binding.relevance());
        reference.put("catalogCode", blankToEmpty(binding.catalogCode()));
        reference.put("catalogVersion", binding.catalogVersion() == null ? "" : binding.catalogVersion());
        reference.put("checklistItemCode", blankToEmpty(binding.checklistItemCode()));
        reference.put("notes", blankToEmpty(binding.notes()));
        reference.put("metadata", metadata(
                "LEGAL_DOMAIN_BINDING",
                Map.of(
                        "bindingId", binding.id(),
                        "articleVersionId", articleVersion == null ? "" : articleVersion.id())));
        return Map.copyOf(reference);
    }

    private List<Map<String, Object>> corpusReferences(
            List<Map<String, Object>> catalogBindings,
            String reportType,
            LocalDate effectiveDate,
            List<Map<String, Object>> existingReferences
    ) {
        if (!shouldResolveCorpusReferences(catalogBindings, reportType)) {
            return List.of();
        }
        var candidates = new ArrayList<Map<String, Object>>();
        var seen = new LinkedHashSet<String>();
        for (var request : corpusSearchRequests(catalogBindings)) {
            var rows = nullToEmptyRows(articleVersionRepository.searchLatestArticles(
                    request.query(),
                    true,
                    request.actCode(),
                    true,
                    "",
                    false,
                    "",
                    false,
                    effectiveDate,
                    FakeLegalSourceClient.DEFAULT_SOURCE_CODE,
                    PageRequest.of(0, CORPUS_REFERENCE_LIMIT_PER_QUERY)));
            for (var row : rows) {
                var reference = corpusReference(row, request);
                var referenceId = text(reference.get("referenceId"));
                if (!referenceId.isBlank() && seen.add(referenceId) && !containsReference(existingReferences, referenceId)) {
                    candidates.add(reference);
                }
            }
        }
        return List.copyOf(candidates);
    }

    private boolean shouldResolveCorpusReferences(List<Map<String, Object>> catalogBindings, String reportType) {
        var normalizedReportType = text(reportType);
        if (CONSTRUCTION_SUPERVISION_REPORT_TYPES.contains(normalizedReportType)) {
            return true;
        }
        return catalogBindings.stream()
                .map(binding -> text(binding.get("catalogCode")))
                .anyMatch(catalogCode -> catalogCode.contains("CONSTRUCTION_SUPERVISION"));
    }

    private List<CorpusSearchRequest> corpusSearchRequests(List<Map<String, Object>> catalogBindings) {
        var requests = new ArrayList<CorpusSearchRequest>();
        requests.add(new CorpusSearchRequest("BUILDING_ACT", "공사감리", "REPORT_SUPERVISION_ANCHOR"));
        requests.add(new CorpusSearchRequest("BUILDING_ACT", "감리", "REPORT_SUPERVISION_ANCHOR"));
        requests.add(new CorpusSearchRequest("CONSTRUCTION_SUPERVISION_DETAILED_STANDARD", "감리", "DETAILED_STANDARD_ANCHOR"));
        catalogBindings.stream()
                .flatMap(binding -> List.of(
                        text(binding.get("inspectionItemName")),
                        text(binding.get("basis"))).stream())
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(3)
                .forEach(query -> requests.add(new CorpusSearchRequest(
                        "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD",
                        query,
                        "CATALOG_ITEM_HINT")));
        var seen = new LinkedHashSet<String>();
        return requests.stream()
                .filter(request -> seen.add(request.actCode() + ":" + request.query()))
                .toList();
    }

    private Map<String, Object> corpusReference(LegalArticleCorpusRow row, CorpusSearchRequest request) {
        var reference = new LinkedHashMap<String, Object>();
        reference.put("referenceId", referenceId(row));
        reference.put("actId", row.actId());
        reference.put("actCode", row.actCode());
        reference.put("actName", row.actName());
        reference.put("actType", row.actType());
        reference.put("articleId", row.articleId());
        reference.put("articleKey", row.articleKey());
        reference.put("articleNo", blankToEmpty(row.articleNo()));
        reference.put("articleTitle", blankToEmpty(row.articleTitle()));
        reference.put("legalVersionId", row.legalVersionId());
        reference.put("sourceVersionKey", blankToEmpty(row.sourceVersionKey()));
        reference.put("effectiveDate", row.effectiveDate() == null ? "" : row.effectiveDate().toString());
        reference.put("bindingScope", "LEGAL_CORPUS_SEARCH");
        reference.put("bindingKey", request.reason());
        reference.put("relevance", "CANDIDATE");
        reference.put("catalogCode", "");
        reference.put("catalogVersion", "");
        reference.put("checklistItemCode", "");
        reference.put("notes", "Resolved from synchronized legal corpus search before legal-risk review.");
        reference.put("metadata", metadata(
                "LEGAL_CORPUS_SEARCH",
                Map.of(
                        "sourceCode", blankToEmpty(row.sourceCode()),
                        "sourceUrl", blankToEmpty(row.sourceUrl()),
                        "articleVersionId", row.articleVersionId(),
                        "contentHash", blankToEmpty(row.contentHash()),
                        "searchActCode", request.actCode(),
                        "searchQuery", request.query(),
                        "resolutionReason", request.reason())));
        return Map.copyOf(reference);
    }

    private String referenceId(LegalArticleCorpusRow row) {
        var id = row.actCode();
        if (!isBlank(row.articleKey())) {
            id += ":" + row.articleKey();
        }
        if (!isBlank(row.sourceVersionKey())) {
            id += "@" + row.sourceVersionKey();
        }
        return id;
    }

    private List<Map<String, Object>> mergeReferences(
            List<Map<String, Object>> bindingReferences,
            List<Map<String, Object>> corpusReferences
    ) {
        var references = new ArrayList<Map<String, Object>>();
        var seen = new LinkedHashSet<String>();
        for (var reference : bindingReferences) {
            var referenceId = text(reference.get("referenceId"));
            if (referenceId.isBlank() || seen.add(referenceId)) {
                references.add(reference);
            }
        }
        for (var reference : corpusReferences) {
            var referenceId = text(reference.get("referenceId"));
            if (referenceId.isBlank() || seen.add(referenceId)) {
                references.add(reference);
            }
        }
        return List.copyOf(references);
    }

    private boolean containsReference(List<Map<String, Object>> references, String referenceId) {
        return references.stream()
                .map(reference -> text(reference.get("referenceId")))
                .anyMatch(referenceId::equals);
    }

    private Map<String, Object> metadata(String resolutionSource, Map<String, Object> values) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("resolutionSource", resolutionSource);
        if (values != null) {
            values.forEach((key, value) -> {
                if (key != null && value != null) {
                    metadata.put(key, value);
                }
            });
        }
        return Map.copyOf(metadata);
    }

    private String referenceId(LegalAct act, LegalArticle article, LegalVersion version) {
        var id = act.actCode();
        if (article != null) {
            id += ":" + article.articleKey();
        }
        if (version != null) {
            id += "@" + version.sourceVersionKey();
        }
        return id;
    }

    private String articleNo(LegalArticle article, LegalArticleVersion articleVersion) {
        if (articleVersion != null && !isBlank(articleVersion.articleNo())) {
            return articleVersion.articleNo();
        }
        return article == null ? "" : article.articleNo();
    }

    private String articleTitle(LegalArticle article, LegalArticleVersion articleVersion) {
        if (articleVersion != null && !isBlank(articleVersion.articleTitle())) {
            return articleVersion.articleTitle();
        }
        return article == null ? "" : blankToEmpty(article.articleTitle());
    }

    private boolean effective(LocalDate targetDate, LocalDate from, LocalDate to) {
        if (targetDate == null) {
            return true;
        }
        if (from != null && targetDate.isBefore(from)) {
            return false;
        }
        return to == null || !targetDate.isAfter(to);
    }

    private List<LegalDomainBinding> nullToEmpty(List<LegalDomainBinding> bindings) {
        return bindings == null ? List.of() : bindings;
    }

    private List<LegalArticleCorpusRow> nullToEmptyRows(List<LegalArticleCorpusRow> rows) {
        return rows == null ? List.of() : rows;
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record EngineLegalReferenceBindingResult(
            List<Map<String, Object>> legalReferences,
            Map<String, Object> metadata
    ) {
        public EngineLegalReferenceBindingResult {
            legalReferences = legalReferences == null ? List.of() : List.copyOf(legalReferences);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    private record CorpusSearchRequest(String actCode, String query, String reason) {
    }
}

package com.archdox.cloud.engine.application;

import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalArticle;
import com.archdox.cloud.legal.domain.LegalArticleVersion;
import com.archdox.cloud.legal.domain.LegalDomainBinding;
import com.archdox.cloud.legal.domain.LegalVersion;
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
import org.springframework.stereotype.Service;

@Service
public class EngineLegalReferenceBindingService {
    private static final String ACTIVE = "ACTIVE";

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
        var references = bindings.stream()
                .filter(binding -> binding.id() != null && seen.add(binding.id()))
                .filter(binding -> effective(effectiveDate, binding.effectiveFrom(), binding.effectiveTo()))
                .map(this::reference)
                .filter(reference -> !reference.isEmpty())
                .toList();

        return new EngineLegalReferenceBindingResult(
                references,
                Map.of(
                        "legalReferenceBindingApplied", !bindings.isEmpty(),
                        "legalReferenceCount", references.size(),
                        "source", "LEGAL_DOMAIN_BINDINGS"));
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
        return Map.copyOf(reference);
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
}

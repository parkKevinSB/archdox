package com.archdox.cloud.legal.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalArticle;
import com.archdox.cloud.legal.domain.LegalDomainBinding;
import com.archdox.cloud.legal.dto.CreateLegalDomainBindingRequest;
import com.archdox.cloud.legal.dto.LegalLawArticleResponse;
import com.archdox.cloud.legal.dto.LegalLawSearchResponse;
import com.archdox.cloud.legal.dto.LegalDomainBindingResponse;
import com.archdox.cloud.legal.dto.UpdateLegalDomainBindingRequest;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleRepository;
import com.archdox.cloud.legal.infra.LegalDomainBindingRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegalDomainBindingAdminService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";

    private final PlatformAdminService platformAdminService;
    private final LegalDomainBindingRepository bindingRepository;
    private final LegalActRepository actRepository;
    private final LegalArticleRepository articleRepository;
    private final OperationEventService operationEventService;
    private final LegalCorpusReadService legalCorpusReadService;

    public LegalDomainBindingAdminService(
            PlatformAdminService platformAdminService,
            LegalDomainBindingRepository bindingRepository,
            LegalActRepository actRepository,
            LegalArticleRepository articleRepository,
            OperationEventService operationEventService,
            LegalCorpusReadService legalCorpusReadService
    ) {
        this.platformAdminService = platformAdminService;
        this.bindingRepository = bindingRepository;
        this.actRepository = actRepository;
        this.articleRepository = articleRepository;
        this.operationEventService = operationEventService;
        this.legalCorpusReadService = legalCorpusReadService;
    }

    @Transactional(readOnly = true)
    public LegalLawSearchResponse searchLaw(
            UserPrincipal principal,
            String query,
            String actCode,
            String actName,
            String articleNo,
            LocalDate effectiveDate,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        return legalCorpusReadService.search(query, actCode, actName, articleNo, effectiveDate, limit);
    }

    @Transactional(readOnly = true)
    public LegalLawArticleResponse getLawArticle(
            UserPrincipal principal,
            Long articleVersionId,
            Long articleId,
            String actCode,
            String articleNo,
            LocalDate effectiveDate
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        return legalCorpusReadService.getArticle(articleVersionId, articleId, actCode, articleNo, effectiveDate);
    }

    @Transactional(readOnly = true)
    public List<LegalDomainBindingResponse> bindings(
            UserPrincipal principal,
            String status,
            String bindingScope,
            String bindingKey,
            String reportType,
            String catalogCode,
            Integer catalogVersion,
            String checklistItemCode,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var bindings = bindingRepository.search(
                upperBlankToNull(status),
                upperBlankToNull(bindingScope),
                blankToNull(bindingKey),
                blankToNull(reportType),
                blankToNull(catalogCode),
                catalogVersion,
                blankToNull(checklistItemCode),
                PageRequest.of(0, limit(limit)));
        return responses(bindings);
    }

    @Transactional
    public LegalDomainBindingResponse createBinding(UserPrincipal principal, CreateLegalDomainBindingRequest request) {
        platformAdminService.requirePlatformAdmin(principal);
        var now = OffsetDateTime.now();
        var act = requireAct(request.actId());
        var article = requireArticleForAct(request.articleId(), act.id());
        requireEffectiveRange(request.effectiveFrom(), request.effectiveTo());
        var binding = bindingRepository.save(new LegalDomainBinding(
                normalizedScope(request.bindingScope()),
                effectiveBindingKey(request.bindingKey(), request.reportType(), request.catalogCode(), request.catalogVersion(), request.checklistItemCode()),
                act.id(),
                article == null ? null : article.id(),
                request.reportType(),
                request.catalogCode(),
                request.catalogVersion(),
                request.checklistItemCode(),
                normalizedRelevance(request.relevance()),
                normalizedStatus(request.status()),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.notes(),
                request.metadataJson(),
                now));
        recordEvent("LEGAL_DOMAIN_BINDING_CREATED", "Legal domain binding was created.", binding, principal);
        return response(binding, act, article);
    }

    @Transactional
    public LegalDomainBindingResponse updateBinding(UserPrincipal principal, Long bindingId, UpdateLegalDomainBindingRequest request) {
        platformAdminService.requirePlatformAdmin(principal);
        var binding = requireBinding(bindingId);
        var act = requireAct(request.actId());
        var article = requireArticleForAct(request.articleId(), act.id());
        requireEffectiveRange(request.effectiveFrom(), request.effectiveTo());
        binding.update(
                normalizedScope(request.bindingScope()),
                effectiveBindingKey(request.bindingKey(), request.reportType(), request.catalogCode(), request.catalogVersion(), request.checklistItemCode()),
                act.id(),
                article == null ? null : article.id(),
                request.reportType(),
                request.catalogCode(),
                request.catalogVersion(),
                request.checklistItemCode(),
                normalizedRelevance(request.relevance()),
                normalizedStatus(request.status()),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.notes(),
                request.metadataJson(),
                OffsetDateTime.now());
        recordEvent("LEGAL_DOMAIN_BINDING_UPDATED", "Legal domain binding was updated.", binding, principal);
        return response(binding, act, article);
    }

    @Transactional
    public LegalDomainBindingResponse deactivateBinding(UserPrincipal principal, Long bindingId) {
        platformAdminService.requirePlatformAdmin(principal);
        var binding = requireBinding(bindingId);
        binding.deactivate(OffsetDateTime.now());
        recordEvent("LEGAL_DOMAIN_BINDING_DEACTIVATED", "Legal domain binding was deactivated.", binding, principal);
        return response(binding);
    }

    private List<LegalDomainBindingResponse> responses(List<LegalDomainBinding> bindings) {
        var actIds = bindings.stream().map(LegalDomainBinding::actId).collect(Collectors.toSet());
        var articleIds = bindings.stream()
                .map(LegalDomainBinding::articleId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        var acts = actRepository.findAllById(actIds).stream()
                .collect(Collectors.toMap(LegalAct::id, Function.identity()));
        var articles = articleRepository.findAllById(articleIds).stream()
                .collect(Collectors.toMap(LegalArticle::id, Function.identity()));
        return bindings.stream()
                .map(binding -> response(binding, acts.get(binding.actId()), articles.get(binding.articleId())))
                .toList();
    }

    private LegalDomainBindingResponse response(LegalDomainBinding binding) {
        var act = actRepository.findById(binding.actId()).orElse(null);
        var article = binding.articleId() == null
                ? null
                : articleRepository.findById(binding.articleId()).orElse(null);
        return response(binding, act, article);
    }

    private LegalDomainBindingResponse response(LegalDomainBinding binding, LegalAct act, LegalArticle article) {
        return new LegalDomainBindingResponse(
                binding.id(),
                binding.bindingScope(),
                binding.bindingKey(),
                binding.actId(),
                act == null ? null : act.actCode(),
                act == null ? null : act.actName(),
                act == null ? null : act.actType(),
                binding.articleId(),
                article == null ? null : article.articleNo(),
                article == null ? null : article.articleTitle(),
                binding.reportType(),
                binding.catalogCode(),
                binding.catalogVersion(),
                binding.checklistItemCode(),
                binding.relevance(),
                binding.status(),
                binding.effectiveFrom(),
                binding.effectiveTo(),
                binding.notes(),
                binding.metadataJson(),
                binding.createdAt(),
                binding.updatedAt());
    }

    private LegalDomainBinding requireBinding(Long bindingId) {
        if (bindingId == null) {
            throw new BadRequestException("bindingId is required");
        }
        return bindingRepository.findById(bindingId)
                .orElseThrow(() -> new NotFoundException("Legal domain binding not found"));
    }

    private LegalAct requireAct(Long actId) {
        if (actId == null) {
            throw new BadRequestException("actId is required");
        }
        return actRepository.findById(actId)
                .orElseThrow(() -> new NotFoundException("Legal act not found"));
    }

    private LegalArticle requireArticleForAct(Long articleId, Long actId) {
        if (articleId == null) {
            return null;
        }
        var article = articleRepository.findById(articleId)
                .orElseThrow(() -> new NotFoundException("Legal article not found"));
        if (!actId.equals(article.actId())) {
            throw new BadRequestException(
                    "LEGAL_DOMAIN_BINDING_ARTICLE_ACT_MISMATCH",
                    "errors.legal.domainBindingArticleActMismatch",
                    "Legal article does not belong to the selected act.");
        }
        return article;
    }

    private void requireEffectiveRange(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BadRequestException(
                    "LEGAL_DOMAIN_BINDING_EFFECTIVE_RANGE_INVALID",
                    "errors.legal.domainBindingEffectiveRangeInvalid",
                    "effectiveTo must be on or after effectiveFrom.");
        }
    }

    private String normalizedScope(String value) {
        return requiredUpper(value, "bindingScope");
    }

    private String normalizedRelevance(String value) {
        return requiredUpper(value == null ? "REFERENCE" : value, "relevance");
    }

    private String normalizedStatus(String value) {
        var status = requiredUpper(value == null ? ACTIVE : value, "status");
        if (ACTIVE.equals(status) || INACTIVE.equals(status)) {
            return status;
        }
        throw new BadRequestException(
                "LEGAL_DOMAIN_BINDING_STATUS_INVALID",
                "errors.legal.domainBindingStatusInvalid",
                "Legal domain binding status must be ACTIVE or INACTIVE.");
    }

    private String effectiveBindingKey(
            String bindingKey,
            String reportType,
            String catalogCode,
            Integer catalogVersion,
            String checklistItemCode
    ) {
        var explicit = blankToNull(bindingKey);
        if (explicit != null) {
            return explicit;
        }
        var checklist = blankToNull(checklistItemCode);
        var catalog = blankToNull(catalogCode);
        if (catalog != null && catalogVersion != null && checklist != null) {
            return catalog + ":v" + catalogVersion + ":" + checklist;
        }
        var report = blankToNull(reportType);
        if (report != null) {
            return report;
        }
        throw new BadRequestException("bindingKey is required");
    }

    private String requiredUpper(String value, String fieldName) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String upperBlankToNull(String value) {
        var normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int limit(Integer value) {
        return Math.max(1, Math.min(value == null ? DEFAULT_LIMIT : value, MAX_LIMIT));
    }

    private void recordEvent(String eventType, String message, LegalDomainBinding binding, UserPrincipal principal) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("bindingId", binding.id());
        payload.put("bindingScope", binding.bindingScope());
        payload.put("bindingKey", binding.bindingKey());
        payload.put("actId", binding.actId());
        if (binding.articleId() != null) {
            payload.put("articleId", binding.articleId());
        }
        payload.put("status", binding.status());
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                eventType,
                "legal-domain-binding",
                "binding:" + binding.id(),
                "LEGAL_DOMAIN_BINDING",
                binding.id(),
                principal.userId(),
                null,
                message,
                Map.copyOf(payload));
    }
}

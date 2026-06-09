package com.archdox.cloud.legal.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalArticle;
import com.archdox.cloud.legal.domain.LegalDomainBinding;
import com.archdox.cloud.legal.dto.CreateLegalDomainBindingRequest;
import com.archdox.cloud.legal.dto.LegalDomainBindingAutoGenerateResponse;
import com.archdox.cloud.legal.dto.LegalLawArticleResponse;
import com.archdox.cloud.legal.dto.LegalLawSearchResponse;
import com.archdox.cloud.legal.dto.LegalLawSearchResultResponse;
import com.archdox.cloud.legal.dto.LegalDomainBindingResponse;
import com.archdox.cloud.legal.dto.UpdateLegalDomainBindingRequest;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleRepository;
import com.archdox.cloud.legal.infra.LegalDomainBindingRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.supervisioncatalog.application.SupervisionDomainCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegalDomainBindingAdminService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";
    private static final String CONSTRUCTION_DAILY_SUPERVISION_LOG = "CONSTRUCTION_DAILY_SUPERVISION_LOG";
    private static final String CONSTRUCTION_SUPERVISION_REPORT = "CONSTRUCTION_SUPERVISION_REPORT";
    private static final String BUILDING_ACT = "BUILDING_ACT";
    private static final String CONSTRUCTION_SUPERVISION_DETAILED_STANDARD = "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD";

    private final PlatformAdminService platformAdminService;
    private final LegalDomainBindingRepository bindingRepository;
    private final LegalActRepository actRepository;
    private final LegalArticleRepository articleRepository;
    private final OperationEventService operationEventService;
    private final LegalCorpusReadService legalCorpusReadService;
    private final SupervisionDomainCatalogService catalogService;

    public LegalDomainBindingAdminService(
            PlatformAdminService platformAdminService,
            LegalDomainBindingRepository bindingRepository,
            LegalActRepository actRepository,
            LegalArticleRepository articleRepository,
            OperationEventService operationEventService,
            LegalCorpusReadService legalCorpusReadService,
            SupervisionDomainCatalogService catalogService
    ) {
        this.platformAdminService = platformAdminService;
        this.bindingRepository = bindingRepository;
        this.actRepository = actRepository;
        this.articleRepository = articleRepository;
        this.operationEventService = operationEventService;
        this.legalCorpusReadService = legalCorpusReadService;
        this.catalogService = catalogService;
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

    @Transactional
    public LegalDomainBindingAutoGenerateResponse autoGenerateConstructionSupervisionBindings(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        var now = OffsetDateTime.now();
        var catalog = catalogService.get(catalogService.defaultConstructionCatalogCode());
        var catalogCode = text(catalog.path("catalogCode").asText(catalogService.defaultConstructionCatalogCode()));
        var catalogVersion = catalog.path("version").canConvertToInt()
                ? catalog.path("version").asInt()
                : catalogService.defaultConstructionCatalogVersion();
        var items = constructionCatalogItems(catalog);
        var primaryReference = firstLegalReference(
                BUILDING_ACT,
                List.of("공사감리", "감리"));
        var supportingReference = firstLegalReference(
                CONSTRUCTION_SUPERVISION_DETAILED_STANDARD,
                List.of("단계별 감리 체크리스트", "감리 체크리스트", "감리", ""));
        if (supportingReference == null) {
            throw new BadRequestException(
                    "LEGAL_DOMAIN_BINDING_AUTOGENERATE_REFERENCE_NOT_FOUND",
                    "errors.legal.domainBindingAutoGenerateReferenceNotFound",
                    "Run legal Open API sync before generating construction supervision legal bindings.");
        }

        var created = new ArrayList<LegalDomainBinding>();
        var reportTypeCreated = 0;
        var reportTypeSkipped = 0;
        if (primaryReference != null) {
            for (var reportType : List.of(CONSTRUCTION_DAILY_SUPERVISION_LOG, CONSTRUCTION_SUPERVISION_REPORT)) {
                var key = reportType + ":BUILDING_ACT_SUPERVISION";
                if (bindingExists("REPORT_TYPE", key, reportType, null, null, null)) {
                    reportTypeSkipped++;
                } else {
                    created.add(bindingRepository.save(bindingFromReference(
                            "REPORT_TYPE",
                            key,
                            primaryReference,
                            reportType,
                            null,
                            null,
                            null,
                            "PRIMARY",
                            "Auto-generated report type legal basis for construction supervision.",
                            Map.of(
                                    "autoGenerated", true,
                                    "autoGenerateMode", "CONSTRUCTION_SUPERVISION_DEFAULT",
                                    "referenceRole", "PRIMARY_REPORT_TYPE"),
                            now)));
                    reportTypeCreated++;
                }
            }
        }
        for (var reportType : List.of(CONSTRUCTION_DAILY_SUPERVISION_LOG, CONSTRUCTION_SUPERVISION_REPORT)) {
            var key = reportType + ":CONSTRUCTION_SUPERVISION_DETAILED_STANDARD";
            if (bindingExists("REPORT_TYPE", key, reportType, null, null, null)) {
                reportTypeSkipped++;
            } else {
                created.add(bindingRepository.save(bindingFromReference(
                        "REPORT_TYPE",
                        key,
                        supportingReference,
                        reportType,
                        null,
                        null,
                        null,
                        primaryReference == null ? "PRIMARY" : "SUPPORTING",
                        "Auto-generated report type legal basis from the synchronized construction supervision detailed standard.",
                        Map.of(
                                "autoGenerated", true,
                                "autoGenerateMode", "CONSTRUCTION_SUPERVISION_DEFAULT",
                                "referenceRole", "SUPPORTING_REPORT_TYPE"),
                        now)));
                reportTypeCreated++;
            }
        }

        var itemSkipped = 0;
        var itemCreated = 0;
        for (var item : items) {
            var key = catalogCode + ":v" + catalogVersion + ":" + item.itemCode();
            if (bindingExists("CATALOG_ITEM", key, null, catalogCode, catalogVersion, item.itemCode())) {
                itemSkipped++;
                continue;
            }
            created.add(bindingRepository.save(bindingFromReference(
                    "CATALOG_ITEM",
                    key,
                    supportingReference,
                    CONSTRUCTION_DAILY_SUPERVISION_LOG,
                    catalogCode,
                    catalogVersion,
                    item.itemCode(),
                    "REFERENCE",
                    "Auto-generated broad legal basis from the construction supervision catalog. Refine manually when a more specific article is needed.",
                    Map.of(
                            "autoGenerated", true,
                            "autoGenerateMode", "CONSTRUCTION_SUPERVISION_CATALOG_DEFAULT",
                            "tradeCode", item.tradeCode(),
                            "tradeName", item.tradeName(),
                            "processCode", item.processCode(),
                            "processName", item.processName(),
                            "inspectionItemName", item.itemName(),
                            "basis", item.basis()),
                    now)));
            itemCreated++;
        }
        created.forEach(binding -> recordEvent(
                "LEGAL_DOMAIN_BINDING_AUTOGENERATED",
                "Legal domain binding was auto-generated.",
                binding,
                principal));

        var samples = created.stream()
                .limit(10)
                .map(this::response)
                .toList();
        return new LegalDomainBindingAutoGenerateResponse(
                "CONSTRUCTION_SUPERVISION_DEFAULT",
                catalogCode,
                catalogVersion,
                items.size(),
                itemCreated + reportTypeCreated,
                itemSkipped + reportTypeSkipped,
                reportTypeCreated,
                reportTypeSkipped,
                referenceLabel(primaryReference),
                referenceLabel(supportingReference),
                samples,
                "공사감리 기본 법령 바인딩을 자동 생성했습니다. 기존 바인딩은 유지하고 없는 항목만 추가했습니다.");
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

    private List<ConstructionCatalogItem> constructionCatalogItems(JsonNode catalog) {
        var items = new ArrayList<ConstructionCatalogItem>();
        var seen = new LinkedHashSet<String>();
        var trades = catalog.path("trades");
        if (!trades.isArray()) {
            return List.of();
        }
        for (var trade : trades) {
            var tradeCode = text(trade.path("code").asText(""));
            var tradeName = text(trade.path("name").asText(""));
            var processGroups = trade.path("processGroups");
            if (!processGroups.isArray()) {
                continue;
            }
            for (var processGroup : processGroups) {
                var processCode = text(processGroup.path("code").asText(""));
                var processName = text(processGroup.path("name").asText(""));
                var groupItems = processGroup.path("items");
                if (!groupItems.isArray()) {
                    continue;
                }
                for (var item : groupItems) {
                    var itemCode = text(item.path("code").asText(""));
                    if (itemCode.isBlank() || !seen.add(itemCode)) {
                        continue;
                    }
                    items.add(new ConstructionCatalogItem(
                            tradeCode,
                            tradeName,
                            processCode,
                            processName,
                            itemCode,
                            text(item.path("name").asText("")),
                            text(item.path("basis").asText(""))));
                }
            }
        }
        return List.copyOf(items);
    }

    private LegalLawSearchResultResponse firstLegalReference(String actCode, List<String> queries) {
        for (var query : queries) {
            var normalizedQuery = blankToNull(query);
            var result = legalCorpusReadService.search(
                    normalizedQuery,
                    actCode,
                    null,
                    null,
                    null,
                    1);
            if (!result.items().isEmpty()) {
                return result.items().get(0);
            }
        }
        return null;
    }

    private boolean bindingExists(
            String bindingScope,
            String bindingKey,
            String reportType,
            String catalogCode,
            Integer catalogVersion,
            String checklistItemCode
    ) {
        return !bindingRepository.search(
                null,
                bindingScope,
                bindingKey,
                reportType,
                catalogCode,
                catalogVersion,
                checklistItemCode,
                PageRequest.of(0, 1)).isEmpty();
    }

    private LegalDomainBinding bindingFromReference(
            String bindingScope,
            String bindingKey,
            LegalLawSearchResultResponse reference,
            String reportType,
            String catalogCode,
            Integer catalogVersion,
            String checklistItemCode,
            String relevance,
            String notes,
            Map<String, Object> metadata,
            OffsetDateTime now
    ) {
        var bindingMetadata = new LinkedHashMap<String, Object>();
        if (metadata != null) {
            bindingMetadata.putAll(metadata);
        }
        bindingMetadata.put("sourceBacked", true);
        bindingMetadata.put("referenceId", reference.referenceId());
        bindingMetadata.put("actCode", reference.actCode());
        bindingMetadata.put("actName", reference.actName());
        bindingMetadata.put("articleNo", valueOrEmpty(reference.articleNo()));
        bindingMetadata.put("articleTitle", valueOrEmpty(reference.articleTitle()));
        bindingMetadata.put("sourceVersionKey", valueOrEmpty(reference.sourceVersionKey()));
        return new LegalDomainBinding(
                bindingScope,
                bindingKey,
                reference.actId(),
                reference.articleId(),
                reportType,
                catalogCode,
                catalogVersion,
                checklistItemCode,
                relevance,
                ACTIVE,
                null,
                null,
                notes,
                Map.copyOf(bindingMetadata),
                now);
    }

    private String referenceLabel(LegalLawSearchResultResponse reference) {
        if (reference == null) {
            return "";
        }
        return Stream.of(reference.actName(), reference.articleNo(), reference.articleTitle())
                .map(this::blankToNull)
                .filter(value -> value != null)
                .collect(Collectors.joining(" "));
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

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
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

    private record ConstructionCatalogItem(
            String tradeCode,
            String tradeName,
            String processCode,
            String processName,
            String itemCode,
            String itemName,
            String basis
    ) {
    }
}

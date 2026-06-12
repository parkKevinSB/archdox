package com.archdox.cloud.legal.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalArticle;
import com.archdox.cloud.legal.domain.LegalDomainBinding;
import com.archdox.cloud.legal.dto.CreateLegalDomainBindingRequest;
import com.archdox.cloud.legal.dto.LegalDomainBindingAutoGenerateResponse;
import com.archdox.cloud.legal.dto.LegalDomainBindingCoverageItemResponse;
import com.archdox.cloud.legal.dto.LegalDomainBindingCoverageResponse;
import com.archdox.cloud.legal.dto.LegalDomainBindingResponse;
import com.archdox.cloud.legal.dto.LegalLawArticleResponse;
import com.archdox.cloud.legal.dto.LegalLawSearchResponse;
import com.archdox.cloud.legal.dto.LegalLawSearchResultResponse;
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

    @Transactional(readOnly = true)
    public LegalDomainBindingCoverageResponse constructionSupervisionCoverage(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        var catalog = catalogService.get(catalogService.defaultConstructionCatalogCode());
        var catalogCode = text(catalog.path("catalogCode").asText(catalogService.defaultConstructionCatalogCode()));
        var catalogVersion = catalog.path("version").canConvertToInt()
                ? catalog.path("version").asInt()
                : catalogService.defaultConstructionCatalogVersion();
        var catalogName = text(catalog.path("catalogName").asText(""));
        var items = constructionCatalogItems(catalog);
        var bindings = nullToEmptyBindings(bindingRepository.search(
                null,
                "CATALOG_ITEM",
                null,
                null,
                catalogCode,
                catalogVersion,
                null,
                PageRequest.of(0, Math.max(1000, items.size() * 6))));
        var responses = responses(bindings);
        var responseById = responses.stream()
                .filter(response -> response.id() != null)
                .collect(Collectors.toMap(LegalDomainBindingResponse::id, Function.identity(), (left, right) -> left));
        var bindingsByItem = bindings.stream()
                .filter(binding -> !text(binding.checklistItemCode()).isBlank())
                .collect(Collectors.groupingBy(LegalDomainBinding::checklistItemCode, LinkedHashMap::new, Collectors.toList()));

        var coverageItems = new ArrayList<LegalDomainBindingCoverageItemResponse>();
        var activeBoundItemCount = 0;
        var inactiveOnlyItemCount = 0;
        for (var item : items) {
            var itemBindings = bindingsByItem.getOrDefault(item.itemCode(), List.of());
            var activeCount = (int) itemBindings.stream().filter(binding -> ACTIVE.equals(binding.status())).count();
            var inactiveCount = (int) itemBindings.stream().filter(binding -> INACTIVE.equals(binding.status())).count();
            var autoGeneratedCount = (int) itemBindings.stream().filter(this::autoGenerated).count();
            var manualCount = itemBindings.size() - autoGeneratedCount;
            if (activeCount > 0) {
                activeBoundItemCount++;
            } else if (inactiveCount > 0) {
                inactiveOnlyItemCount++;
            }
            coverageItems.add(new LegalDomainBindingCoverageItemResponse(
                    catalogCode,
                    catalogVersion,
                    item.tradeCode(),
                    item.tradeName(),
                    item.processCode(),
                    item.processName(),
                    item.itemCode(),
                    item.itemName(),
                    item.basis(),
                    activeCount,
                    inactiveCount,
                    autoGeneratedCount,
                    manualCount,
                    itemBindings.stream()
                            .map(binding -> responseById.get(binding.id()))
                            .filter(response -> response != null)
                            .toList()));
        }
        var missingItems = coverageItems.stream()
                .filter(item -> item.activeBindingCount() == 0)
                .toList();
        var autoGeneratedBindingCount = (int) bindings.stream().filter(this::autoGenerated).count();
        return new LegalDomainBindingCoverageResponse(
                catalogCode,
                catalogVersion,
                catalogName,
                items.size(),
                activeBoundItemCount,
                missingItems.size(),
                inactiveOnlyItemCount,
                bindings.size(),
                autoGeneratedBindingCount,
                bindings.size() - autoGeneratedBindingCount,
                missingItems,
                coverageItems);
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
        var secondaryItemSkipped = 0;
        var secondaryItemCreated = 0;
        var secondaryReferenceCache = new LinkedHashMap<String, LegalLawSearchResultResponse>();
        for (var item : items) {
            var key = catalogCode + ":v" + catalogVersion + ":" + item.itemCode();
            if (bindingExists("CATALOG_ITEM", key, null, catalogCode, catalogVersion, item.itemCode())) {
                itemSkipped++;
            } else {
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

            for (var selector : secondaryLegalReferenceSelectors(item)) {
                var reference = secondaryReferenceCache.computeIfAbsent(
                        selector.cacheKey(),
                        ignored -> firstLegalReference(selector.actCode(), selector.queries()));
                if (reference == null) {
                    continue;
                }
                var secondaryKey = key + ":" + selector.actCode();
                if (bindingExists("CATALOG_ITEM", secondaryKey, null, catalogCode, catalogVersion, item.itemCode())) {
                    secondaryItemSkipped++;
                    continue;
                }
                created.add(bindingRepository.save(bindingFromReference(
                        "CATALOG_ITEM",
                        secondaryKey,
                        reference,
                        CONSTRUCTION_DAILY_SUPERVISION_LOG,
                        catalogCode,
                        catalogVersion,
                        item.itemCode(),
                        selector.relevance(),
                        "Auto-generated secondary legal reference for the construction supervision catalog item. Treat this as a source-backed review anchor, not a final compliance rule.",
                        secondaryBindingMetadata(item, selector),
                        now)));
                secondaryItemCreated++;
            }
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
                itemCreated + secondaryItemCreated + reportTypeCreated,
                itemSkipped + secondaryItemSkipped + reportTypeSkipped,
                reportTypeCreated,
                reportTypeSkipped,
                referenceLabel(primaryReference),
                referenceLabel(supportingReference),
                samples,
                "공사감리 기본 법령 바인딩과 공종별 2차 법령 바인딩을 자동 생성했습니다. 기존 바인딩은 유지하고 없는 항목만 추가했습니다.");
    }

    private List<LegalDomainBindingResponse> responses(List<LegalDomainBinding> bindings) {
        bindings = nullToEmptyBindings(bindings);
        if (bindings.isEmpty()) {
            return List.of();
        }
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
        var descriptor = domainDescriptor(binding);
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
                descriptor.reportTypeLabel(),
                binding.catalogCode(),
                binding.catalogVersion(),
                descriptor.catalogName(),
                binding.checklistItemCode(),
                descriptor.tradeCode(),
                descriptor.tradeName(),
                descriptor.processCode(),
                descriptor.processName(),
                descriptor.checklistItemName(),
                descriptor.checklistItemBasis(),
                descriptor.bindingDisplayName(),
                binding.relevance(),
                binding.status(),
                binding.effectiveFrom(),
                binding.effectiveTo(),
                binding.notes(),
                binding.metadataJson(),
                binding.createdAt(),
                binding.updatedAt());
    }

    private DomainBindingDescriptor domainDescriptor(LegalDomainBinding binding) {
        var reportTypeLabel = reportTypeLabel(binding.reportType());
        var catalogName = catalogName(binding.catalogCode());
        var item = catalogItem(binding.catalogCode(), binding.checklistItemCode());
        var metadata = binding.metadataJson();
        var tradeCode = item == null ? metadataText(metadata, "tradeCode") : item.tradeCode();
        var tradeName = item == null ? metadataText(metadata, "tradeName") : item.tradeName();
        var processCode = item == null ? metadataText(metadata, "processCode") : item.processCode();
        var processName = item == null ? metadataText(metadata, "processName") : item.processName();
        var itemName = item == null ? metadataText(metadata, "inspectionItemName") : item.itemName();
        var basis = item == null ? metadataText(metadata, "basis") : item.basis();
        var displayName = Stream.of(tradeName, processName, itemName)
                .map(this::blankToNull)
                .filter(value -> value != null)
                .collect(Collectors.joining(" / "));
        if (displayName.isBlank()) {
            displayName = firstNonBlank(reportTypeLabel, binding.checklistItemCode(), binding.reportType(), binding.bindingKey());
        }
        return new DomainBindingDescriptor(
                reportTypeLabel,
                catalogName,
                tradeCode,
                tradeName,
                processCode,
                processName,
                itemName,
                basis,
                displayName);
    }

    private ConstructionCatalogItem catalogItem(String catalogCode, String checklistItemCode) {
        var normalizedCatalogCode = blankToNull(catalogCode);
        var normalizedItemCode = blankToNull(checklistItemCode);
        if (normalizedCatalogCode == null || normalizedItemCode == null) {
            return null;
        }
        try {
            return constructionCatalogItems(catalogService.get(normalizedCatalogCode)).stream()
                    .filter(item -> normalizedItemCode.equals(item.itemCode()))
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String catalogName(String catalogCode) {
        var normalizedCatalogCode = blankToNull(catalogCode);
        if (normalizedCatalogCode == null) {
            return "";
        }
        try {
            return text(catalogService.get(normalizedCatalogCode).path("catalogName").asText(""));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private String reportTypeLabel(String reportType) {
        var normalized = blankToNull(reportType);
        if (normalized == null) {
            return "";
        }
        if (CONSTRUCTION_DAILY_SUPERVISION_LOG.equals(normalized)) {
            return "공사감리일지";
        }
        if (CONSTRUCTION_SUPERVISION_REPORT.equals(normalized)) {
            return "감리보고서";
        }
        return normalized;
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

    private List<SecondaryLegalReferenceSelector> secondaryLegalReferenceSelectors(ConstructionCatalogItem item) {
        var selectors = new ArrayList<SecondaryLegalReferenceSelector>();
        var tradeCode = item.tradeCode();
        if (matchesTrade(tradeCode,
                "TEMPORARY_WORKS",
                "EARTH_WORKS",
                "PILE_AND_FOUNDATION",
                "FORMWORK",
                "REINFORCED_CONCRETE",
                "STEEL_FRAME",
                "MASONRY_ALC_PANEL")) {
            selectors.add(selector(
                    "STRUCTURAL_COMMON",
                    "구조·품질 공통 기준",
                    "BUILDING_STRUCTURAL_STANDARD_RULE",
                    List.of("구조", ""),
                    "SUPPORTING",
                    "STRUCTURAL_REFERENCE"));
            selectors.add(selector(
                    "CONSTRUCTION_QUALITY",
                    "건설공사 품질관리 기준",
                    "CONSTRUCTION_WORK_QUALITY_MANAGEMENT_GUIDELINE",
                    List.of("품질관리", "품질", ""),
                    "REFERENCE",
                    "QUALITY_REFERENCE"));
        }
        if (matchesTrade(tradeCode, "EARTH_WORKS", "PILE_AND_FOUNDATION")) {
            selectors.add(selector(
                    "GEOTECHNICAL",
                    "지반·기초 기준",
                    "GEOTECHNICAL_DESIGN_AND_GROUND_WORK_STANDARD_SPECIFICATION",
                    List.of("지반", "터파기", "기초", ""),
                    "SUPPORTING",
                    "TRADE_REFERENCE"));
        }
        if (matchesTrade(tradeCode, "TEMPORARY_WORKS")) {
            selectors.add(selector(
                    "TEMPORARY_WORK",
                    "가설공사 기준",
                    "TEMPORARY_FACILITY_DESIGN_AND_TEMPORARY_WORK_STANDARD_SPECIFICATION",
                    List.of("가설", "규준틀", ""),
                    "SUPPORTING",
                    "TRADE_REFERENCE"));
        }
        if (matchesTrade(tradeCode, "REINFORCED_CONCRETE", "FORMWORK")) {
            selectors.add(selector(
                    "CONCRETE_WORK",
                    "콘크리트 구조·공사 기준",
                    "CONCRETE_STRUCTURE_DESIGN_AND_WORK_STANDARD_SPECIFICATION",
                    List.of("콘크리트", "철근", "거푸집", ""),
                    "SUPPORTING",
                    "TRADE_REFERENCE"));
        }
        if (matchesTrade(tradeCode, "STEEL_FRAME")) {
            selectors.add(selector(
                    "STEEL_WORK",
                    "강구조 공사 기준",
                    "STEEL_STRUCTURE_WORK_STANDARD_SPECIFICATION",
                    List.of("강구조", "철골", ""),
                    "SUPPORTING",
                    "TRADE_REFERENCE"));
        }
        if (matchesTrade(tradeCode,
                "INSULATION",
                "WINDOWS_DOORS",
                "GLASS_WORKS",
                "CURTAIN_WALL",
                "ROOF_GUTTER")) {
            selectors.add(selector(
                    "ENERGY_ENVELOPE",
                    "단열·창호·외피 에너지 기준",
                    "BUILDING_ENERGY_SAVING_DESIGN_STANDARD",
                    List.of("단열", "창", "열관류", ""),
                    "SUPPORTING",
                    "PERFORMANCE_REFERENCE"));
            selectors.add(selector(
                    "GREEN_BUILDING",
                    "녹색건축·에너지 성능 기준",
                    "GREEN_BUILDING_ACT",
                    List.of("에너지", "녹색건축", ""),
                    "REFERENCE",
                    "PERFORMANCE_REFERENCE"));
        }
        if (matchesTrade(tradeCode,
                "PLUMBING_SANITARY",
                "HVAC",
                "PIPING",
                "DUCT",
                "MECHANICAL_AUTOMATION",
                "REFRIGERATION",
                "CLEAN_ROOM",
                "SOUND_VIBRATION_SEISMIC",
                "OUTDOOR_WORKS")) {
            selectors.add(selector(
                    "MECHANICAL_EQUIPMENT",
                    "기계설비 기준",
                    "MECHANICAL_EQUIPMENT_ACT",
                    List.of("기계설비", ""),
                    "SUPPORTING",
                    "TRADE_REFERENCE"));
            selectors.add(selector(
                    "BUILDING_EQUIPMENT",
                    "건축물 설비 기준",
                    "BUILDING_EQUIPMENT_STANDARD_RULE",
                    List.of("설비", ""),
                    "REFERENCE",
                    "TRADE_REFERENCE"));
        }
        if (matchesTrade(tradeCode, "GAS_FACILITY")) {
            selectors.add(selector(
                    "CITY_GAS",
                    "도시가스 설비 기준",
                    "CITY_GAS_BUSINESS_ACT",
                    List.of("가스", ""),
                    "SUPPORTING",
                    "TRADE_REFERENCE"));
            selectors.add(selector(
                    "LPG",
                    "액화석유가스 설비 기준",
                    "LIQUEFIED_PETROLEUM_GAS_SAFETY_BUSINESS_ACT",
                    List.of("액화석유가스", "가스", ""),
                    "REFERENCE",
                    "TRADE_REFERENCE"));
            selectors.add(selector(
                    "HIGH_PRESSURE_GAS",
                    "고압가스 설비 기준",
                    "HIGH_PRESSURE_GAS_SAFETY_CONTROL_ACT",
                    List.of("고압가스", "가스", ""),
                    "REFERENCE",
                    "TRADE_REFERENCE"));
        }
        if (matchesTrade(tradeCode, "RENEWABLE_ENERGY")) {
            selectors.add(selector(
                    "RENEWABLE_ENERGY",
                    "신재생에너지 설비 기준",
                    "NEW_RENEWABLE_ENERGY_ACT",
                    List.of("신에너지", "재생에너지", ""),
                    "SUPPORTING",
                    "TRADE_REFERENCE"));
        }
        if (matchesTrade(tradeCode,
                "POWER_RECEIVING_LEAD_IN",
                "EMERGENCY_POWER",
                "INDOOR_WIRING",
                "LIGHTING",
                "POWER_FACILITY",
                "MONITORING_CONTROL",
                "LIGHTNING_GROUNDING")) {
            selectors.add(selector(
                    "ELECTRICAL_SAFETY",
                    "전기안전 기준",
                    "ELECTRICAL_SAFETY_MANAGEMENT_ACT",
                    List.of("검사", "전기안전", ""),
                    "SUPPORTING",
                    "TRADE_REFERENCE"));
            selectors.add(selector(
                    "ELECTRICAL_TECHNICAL",
                    "전기설비 기술기준",
                    "ELECTRICAL_EQUIPMENT_TECHNICAL_STANDARD",
                    List.of("전기설비", "접지", ""),
                    "SUPPORTING",
                    "TECHNICAL_REFERENCE"));
            selectors.add(selector(
                    "ELECTRICAL_CONSTRUCTION",
                    "전기공사 기준",
                    "ELECTRICAL_CONSTRUCTION_BUSINESS_ACT",
                    List.of("전기공사", ""),
                    "REFERENCE",
                    "TRADE_REFERENCE"));
        }
        if (matchesTrade(tradeCode, "COMMUNICATION", "LOW_VOLTAGE_SYSTEM")) {
            selectors.add(selector(
                    "INFORMATION_COMMUNICATION",
                    "정보통신공사 기준",
                    "INFORMATION_COMMUNICATIONS_CONSTRUCTION_BUSINESS_ACT",
                    List.of("정보통신공사", "통신", ""),
                    "SUPPORTING",
                    "TRADE_REFERENCE"));
            selectors.add(selector(
                    "GROUNDING_COMMUNICATION",
                    "접지·구내통신설비 기준",
                    "GROUNDING_AND_IN_BUILDING_COMMUNICATION_FACILITY_TECHNICAL_STANDARD",
                    List.of("구내통신", "접지", ""),
                    "REFERENCE",
                    "TECHNICAL_REFERENCE"));
        }
        if (matchesTrade(tradeCode, "MECHANICAL_FIRE_FIGHTING", "ELECTRICAL_FIRE_FIGHTING")) {
            selectors.add(selector(
                    "FIRE_FACILITIES",
                    "소방시설 설치·관리 기준",
                    "FIRE_FACILITIES_INSTALLATION_MANAGEMENT_ACT",
                    List.of("소방시설", "설치", ""),
                    "SUPPORTING",
                    "TRADE_REFERENCE"));
            selectors.add(selector(
                    "FIRE_PREVENTION",
                    "화재예방·안전관리 기준",
                    "FIRE_PREVENTION_SAFETY_MANAGEMENT_ACT",
                    List.of("화재", "안전관리", ""),
                    "REFERENCE",
                    "SAFETY_REFERENCE"));
        }
        if (matchesTrade(tradeCode, "ELEVATOR_MECHANICAL_PARKING")) {
            selectors.add(selector(
                    "ELEVATOR",
                    "승강기 안전관리 기준",
                    "ELEVATOR_SAFETY_MANAGEMENT_ACT",
                    List.of("승강기", "검사", ""),
                    "SUPPORTING",
                    "TRADE_REFERENCE"));
            selectors.add(selector(
                    "MECHANICAL_PARKING",
                    "기계식주차장치 기준",
                    "MECHANICAL_PARKING_DEVICE_SAFETY_AND_INSPECTION_STANDARD",
                    List.of("기계식주차", "주차", ""),
                    "REFERENCE",
                    "TRADE_REFERENCE"));
        }
        if (matchesTrade(tradeCode, "LANDSCAPE")) {
            selectors.add(selector(
                    "LANDSCAPE_WORK",
                    "조경 설계·공사 기준",
                    "LANDSCAPE_DESIGN_AND_WORK_STANDARD_SPECIFICATION",
                    List.of("조경", ""),
                    "SUPPORTING",
                    "TRADE_REFERENCE"));
        }
        if (matchesTrade(tradeCode,
                "EARTH_WORKS",
                "PILE_AND_FOUNDATION",
                "TEMPORARY_WORKS",
                "STEEL_FRAME",
                "REINFORCED_CONCRETE")) {
            selectors.add(selector(
                    "OCCUPATIONAL_SAFETY",
                    "산업안전보건 기준",
                    "OCCUPATIONAL_SAFETY_HEALTH_STANDARDS_RULE",
                    List.of("안전", "작업", ""),
                    "REFERENCE",
                    "SAFETY_REFERENCE"));
        }
        return List.copyOf(selectors);
    }

    private SecondaryLegalReferenceSelector selector(
            String ruleCode,
            String ruleLabel,
            String actCode,
            List<String> queries,
            String relevance,
            String referenceRole
    ) {
        return new SecondaryLegalReferenceSelector(ruleCode, ruleLabel, actCode, queries, relevance, referenceRole);
    }

    private Map<String, Object> secondaryBindingMetadata(
            ConstructionCatalogItem item,
            SecondaryLegalReferenceSelector selector
    ) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("autoGenerated", true);
        metadata.put("autoGenerateMode", "CONSTRUCTION_SUPERVISION_SECONDARY");
        metadata.put("bindingPolicy", "TRADE_GROUP_REFERENCE");
        metadata.put("ruleCode", selector.ruleCode());
        metadata.put("ruleLabel", selector.ruleLabel());
        metadata.put("referenceRole", selector.referenceRole());
        metadata.put("tradeCode", item.tradeCode());
        metadata.put("tradeName", item.tradeName());
        metadata.put("processCode", item.processCode());
        metadata.put("processName", item.processName());
        metadata.put("inspectionItemName", item.itemName());
        metadata.put("basis", item.basis());
        return Map.copyOf(metadata);
    }

    private boolean matchesTrade(String tradeCode, String... candidates) {
        var normalizedTradeCode = firstNonBlank(tradeCode, "");
        for (var candidate : candidates) {
            if (normalizedTradeCode.equals(candidate)) {
                return true;
            }
        }
        return false;
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
            if (result != null && !result.items().isEmpty()) {
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

    private List<LegalDomainBinding> nullToEmptyBindings(List<LegalDomainBinding> bindings) {
        return bindings == null ? List.of() : bindings;
    }

    private boolean autoGenerated(LegalDomainBinding binding) {
        var value = binding.metadataJson().get("autoGenerated");
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value == null ? "" : String.valueOf(value));
    }

    private String metadataText(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return "";
        }
        var value = metadata.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (var value : values) {
            var normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return "";
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

    private record DomainBindingDescriptor(
            String reportTypeLabel,
            String catalogName,
            String tradeCode,
            String tradeName,
            String processCode,
            String processName,
            String checklistItemName,
            String checklistItemBasis,
            String bindingDisplayName
    ) {
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

    private record SecondaryLegalReferenceSelector(
            String ruleCode,
            String ruleLabel,
            String actCode,
            List<String> queries,
            String relevance,
            String referenceRole
    ) {
        private String cacheKey() {
            return actCode + ":" + String.join("|", queries);
        }
    }
}

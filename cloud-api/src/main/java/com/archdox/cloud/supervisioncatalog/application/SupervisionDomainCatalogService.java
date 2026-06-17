package com.archdox.cloud.supervisioncatalog.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.site.domain.SupervisionWorkMode;
import com.archdox.cloud.site.infra.SiteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class SupervisionDomainCatalogService {
    public static final String CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24 =
            "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24";

    private static final Map<String, String> RESOURCE_BY_CODE = Map.of(
            CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24,
            "domain-catalogs/construction-supervision-checklist-2020-12-24.json"
    );

    private final ObjectMapper objectMapper;
    private final SiteRepository siteRepository;
    private final Map<String, JsonNode> catalogCache = new ConcurrentHashMap<>();

    @Autowired
    public SupervisionDomainCatalogService(ObjectMapper objectMapper, SiteRepository siteRepository) {
        this.objectMapper = objectMapper;
        this.siteRepository = siteRepository;
    }

    public SupervisionDomainCatalogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.siteRepository = null;
    }

    public JsonNode get(String catalogCode) {
        return get(catalogCode, null);
    }

    public JsonNode get(String catalogCode, Long siteId) {
        var normalized = normalize(catalogCode);
        var result = (ObjectNode) catalog(normalized).deepCopy();
        enrichSupervisionWorkMode(result, resolveSupervisionWorkMode(siteId));
        return result;
    }

    public String defaultConstructionCatalogCode() {
        return CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24;
    }

    public int defaultConstructionCatalogVersion() {
        return version(catalog(CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24));
    }

    public SupervisionCatalogSelection requireInspectionItemSelection(
            String tradeCode,
            String processCode,
            String inspectionItemCode
    ) {
        var catalog = catalog(CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24);
        var normalizedTradeCode = requiredCode(tradeCode, "tradeCode");
        var normalizedProcessCode = requiredCode(processCode, "processCode");
        var normalizedInspectionItemCode = requiredCode(inspectionItemCode, "inspectionItemCode");

        var trade = findByCode(catalog.path("trades"), normalizedTradeCode)
                .orElseThrow(() -> badSelection(
                        "SUPERVISION_CATALOG_TRADE_NOT_FOUND",
                        "Trade code is not defined in the supervision catalog",
                        normalizedTradeCode));
        var processGroup = findByCode(trade.path("processGroups"), normalizedProcessCode)
                .orElseThrow(() -> badSelection(
                        "SUPERVISION_CATALOG_PROCESS_NOT_FOUND",
                        "Process code is not defined under the selected trade in the supervision catalog",
                        normalizedProcessCode));
        var item = findByCode(processGroup.path("items"), normalizedInspectionItemCode)
                .orElseThrow(() -> badSelection(
                        "SUPERVISION_CATALOG_INSPECTION_ITEM_NOT_FOUND",
                        "Inspection item code is not defined under the selected trade/process in the supervision catalog",
                        normalizedInspectionItemCode));

        return new SupervisionCatalogSelection(
                text(catalog.path("catalogCode")),
                version(catalog),
                normalizedTradeCode,
                text(trade.path("name")),
                normalizedProcessCode,
                text(processGroup.path("name")),
                normalizedInspectionItemCode,
                text(item.path("name")),
                text(item.path("basis")));
    }

    private JsonNode catalog(String normalized) {
        return catalogCache.computeIfAbsent(normalized, this::readCatalog);
    }

    private SupervisionWorkMode resolveSupervisionWorkMode(Long siteId) {
        if (siteId == null) {
            return SupervisionWorkMode.defaultMode();
        }
        if (siteRepository == null) {
            return SupervisionWorkMode.defaultMode();
        }
        var officeId = OfficeContext.requireCurrentOfficeId();
        return siteRepository.findByIdAndOfficeId(siteId, officeId)
                .map(site -> site.supervisionWorkMode())
                .orElse(SupervisionWorkMode.defaultMode());
    }

    private void enrichSupervisionWorkMode(ObjectNode catalog, SupervisionWorkMode selectedMode) {
        var mode = selectedMode == null ? SupervisionWorkMode.defaultMode() : selectedMode;
        catalog.put("selectedSupervisionWorkMode", mode.name());
        catalog.put("selectedSupervisionWorkModeName", supervisionWorkModeName(mode));
        catalog.set("supervisionWorkModes", objectMapper.valueToTree(List.of(
                mode("NON_RESIDENT", "비상주 감리", "22-73", "현재 전사된 기본 공종별 체크리스트입니다."),
                mode("RESIDENT", "상주 감리", "74-126", "상주 공사감리 체크리스트 구간입니다. 상세 항목 전사 후 모드별 필터링 대상입니다."),
                mode("RESPONSIBLE_RESIDENT", "책임상주 감리", "127-178", "책임상주 공사감리 체크리스트 구간입니다. 상세 항목 전사 후 모드별 필터링 대상입니다.")
        )));
        catalog.set("selectedSupervisionWorkModeCatalogCoverage", objectMapper.valueToTree(
                mode == SupervisionWorkMode.NON_RESIDENT
                        ? Map.of(
                                "status", "READY",
                                "referencePages", "22-73",
                                "message", "비상주 공사감리 체크리스트 기준으로 전사된 카탈로그입니다.")
                        : Map.of(
                                "status", "EXTRACTION_PENDING",
                                "referencePages", mode == SupervisionWorkMode.RESIDENT ? "74-126" : "127-178",
                                "message", "현장 감리업무 종류는 저장되었지만, 이 모드의 상세 체크리스트 전사는 다음 단계에서 확장됩니다.")));
    }

    private Map<String, String> mode(String code, String name, String referencePages, String description) {
        return Map.of(
                "code", code,
                "name", name,
                "referencePages", referencePages,
                "description", description);
    }

    private String supervisionWorkModeName(SupervisionWorkMode mode) {
        return switch (mode) {
            case NON_RESIDENT -> "비상주 감리";
            case RESIDENT -> "상주 감리";
            case RESPONSIBLE_RESIDENT -> "책임상주 감리";
        };
    }

    private JsonNode readCatalog(String normalized) {
        var resourcePath = RESOURCE_BY_CODE.get(normalized);
        if (resourcePath == null) {
            throw new NotFoundException(
                    "SUPERVISION_CATALOG_NOT_FOUND",
                    "errors.supervisionCatalog.notFound",
                    "Supervision domain catalog not found");
        }
        try (var input = new ClassPathResource(resourcePath).getInputStream()) {
            return objectMapper.readTree(input);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read supervision domain catalog: " + normalized, ex);
        }
    }

    private java.util.Optional<JsonNode> findByCode(JsonNode array, String code) {
        if (!array.isArray()) {
            return java.util.Optional.empty();
        }
        for (var item : array) {
            if (code.equals(normalize(item.path("code").asText("")))) {
                return java.util.Optional.of(item);
            }
        }
        return java.util.Optional.empty();
    }

    private BadRequestException badSelection(String code, String message, String value) {
        return new BadRequestException(
                code,
                "errors.supervisionCatalog.selectionInvalid",
                message,
                Map.of("value", value));
    }

    private String requiredCode(String value, String fieldName) {
        var normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new BadRequestException(
                    "SUPERVISION_CATALOG_CODE_REQUIRED",
                    "errors.supervisionCatalog.codeRequired",
                    fieldName + " is required for supervision catalog binding",
                    Map.of("field", fieldName));
        }
        return normalized;
    }

    private int version(JsonNode catalog) {
        var version = catalog.path("version");
        if (!version.canConvertToInt()) {
            throw new BadRequestException(
                    "SUPERVISION_CATALOG_VERSION_INVALID",
                    "errors.supervisionCatalog.versionInvalid",
                    "Supervision catalog version is invalid");
        }
        return version.asInt();
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private String normalize(String catalogCode) {
        return catalogCode == null ? "" : catalogCode.trim().toUpperCase();
    }

    public record SupervisionCatalogSelection(
            String catalogCode,
            int catalogVersion,
            String tradeCode,
            String tradeName,
            String processCode,
            String processName,
            String inspectionItemCode,
            String inspectionItemName,
            String basis
    ) {
    }
}

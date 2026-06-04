package com.archdox.cloud.supervisioncatalog.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, JsonNode> catalogCache = new ConcurrentHashMap<>();

    public SupervisionDomainCatalogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode get(String catalogCode) {
        var normalized = normalize(catalogCode);
        return catalog(normalized).deepCopy();
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

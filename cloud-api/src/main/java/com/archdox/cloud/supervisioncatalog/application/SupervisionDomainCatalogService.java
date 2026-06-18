package com.archdox.cloud.supervisioncatalog.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.site.domain.SupervisionWorkMode;
import com.archdox.cloud.site.infra.SiteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
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
        var modeCatalog = selectedModeCatalog(catalog.path("supervisionWorkModeCatalogs"), mode);
        catalog.put("selectedSupervisionWorkMode", mode.name());
        catalog.put("selectedSupervisionWorkModeName", text(modeCatalog.path("name")));
        catalog.set("supervisionWorkModes", objectMapper.valueToTree(List.of(
                modeSummary(catalog.path("supervisionWorkModeCatalogs"), SupervisionWorkMode.NON_RESIDENT),
                modeSummary(catalog.path("supervisionWorkModeCatalogs"), SupervisionWorkMode.RESIDENT),
                modeSummary(catalog.path("supervisionWorkModeCatalogs"), SupervisionWorkMode.RESPONSIBLE_RESIDENT)
        )));
        catalog.set("selectedSupervisionWorkModeCatalog", modeCatalog.deepCopy());
        catalog.set("selectedSupervisionWorkModeCatalogCoverage", objectMapper.valueToTree(modeCoverage(modeCatalog)));
    }

    private Map<String, Object> modeSummary(JsonNode modeCatalogs, SupervisionWorkMode mode) {
        var modeCatalog = selectedModeCatalog(modeCatalogs, mode);
        var summary = new LinkedHashMap<String, Object>();
        summary.put("code", text(modeCatalog.path("code")));
        summary.put("name", text(modeCatalog.path("name")));
        summary.put("status", text(modeCatalog.path("status")));
        summary.put("referencePages", text(modeCatalog.path("referencePages")));
        summary.put("catalogDataSource", text(modeCatalog.path("catalogDataSource")));
        summary.put("description", text(modeCatalog.path("description")));
        return summary;
    }

    private Map<String, Object> modeCoverage(JsonNode modeCatalog) {
        var coverage = new LinkedHashMap<String, Object>();
        coverage.put("status", text(modeCatalog.path("status")));
        coverage.put("referencePages", text(modeCatalog.path("referencePages")));
        coverage.put("catalogDataSource", text(modeCatalog.path("catalogDataSource")));
        coverage.put("dataPolicy", text(modeCatalog.path("dataPolicy")));
        coverage.put("canWriteReports", modeCatalog.path("canWriteReports").asBoolean(true));
        coverage.put("message", text(modeCatalog.path("message")));
        return coverage;
    }

    private JsonNode selectedModeCatalog(JsonNode modeCatalogs, SupervisionWorkMode mode) {
        var configured = modeCatalogs.path(mode.name());
        if (configured.isObject()) {
            return configured;
        }
        return objectMapper.valueToTree(fallbackModeCatalog(mode));
    }

    private Map<String, Object> fallbackModeCatalog(SupervisionWorkMode mode) {
        var fallback = new LinkedHashMap<String, Object>();
        fallback.put("code", mode.name());
        fallback.put("name", switch (mode) {
            case NON_RESIDENT -> "비상주 감리";
            case RESIDENT -> "상주 감리";
            case RESPONSIBLE_RESIDENT -> "책임상주 감리";
        });
        fallback.put("status", mode == SupervisionWorkMode.NON_RESIDENT ? "READY" : "DRAFT_COPY_PENDING_TRANSCRIPTION");
        fallback.put("referencePages", switch (mode) {
            case NON_RESIDENT -> "22-73";
            case RESIDENT -> "74-126";
            case RESPONSIBLE_RESIDENT -> "127-178";
        });
        fallback.put("catalogDataSource", mode == SupervisionWorkMode.NON_RESIDENT
                ? "MODE_SPECIFIC_TRANSCRIPTION"
                : "NON_RESIDENT_COPY_DRAFT");
        fallback.put("dataPolicy", mode == SupervisionWorkMode.NON_RESIDENT ? "CANONICAL" : "SEPARATE_MODE_SLOT");
        fallback.put("canWriteReports", true);
        fallback.put("description", "");
        fallback.put("message", "");
        return fallback;
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
            var manifest = objectMapper.readTree(input);
            return mergeCatalogParts(manifest);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read supervision domain catalog: " + normalized, ex);
        }
    }

    private JsonNode mergeCatalogParts(JsonNode manifest) {
        if (!manifest.isObject()) {
            return manifest;
        }
        var merged = (ObjectNode) manifest.deepCopy();
        var partFiles = manifest.path("partFiles");
        if (!partFiles.isArray() || partFiles.isEmpty()) {
            return merged;
        }
        for (var partFile : partFiles) {
            var resourcePath = text(partFile);
            if (resourcePath.isBlank()) {
                continue;
            }
            mergeCatalogPart(merged, readResource(resourcePath));
        }
        return merged;
    }

    private JsonNode readResource(String resourcePath) {
        try (var input = new ClassPathResource(resourcePath).getInputStream()) {
            return objectMapper.readTree(input);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read supervision domain catalog part: " + resourcePath, ex);
        }
    }

    private void mergeCatalogPart(ObjectNode target, JsonNode part) {
        if (!part.isObject()) {
            return;
        }
        mergeObjectField(target, part, "supervisionWorkModeCatalogs");
        mergeObjectField(target, part, "canonicalAtoms");
        appendArrayField(target, part, "trades");
    }

    private void mergeObjectField(ObjectNode target, JsonNode part, String fieldName) {
        var sourceValue = part.path(fieldName);
        if (!sourceValue.isObject()) {
            return;
        }
        var targetValue = target.withObject("/" + fieldName);
        deepMerge(targetValue, (ObjectNode) sourceValue);
    }

    private void appendArrayField(ObjectNode target, JsonNode part, String fieldName) {
        var sourceValue = part.path(fieldName);
        if (!sourceValue.isArray()) {
            return;
        }
        var targetValue = target.withArray("/" + fieldName);
        for (var item : sourceValue) {
            targetValue.add(item.deepCopy());
        }
    }

    private void deepMerge(ObjectNode target, ObjectNode source) {
        source.fields().forEachRemaining(entry -> {
            var fieldName = entry.getKey();
            var sourceValue = entry.getValue();
            var targetValue = target.get(fieldName);
            if (sourceValue.isObject() && targetValue instanceof ObjectNode targetObject) {
                deepMerge(targetObject, (ObjectNode) sourceValue);
            } else if (sourceValue.isArray() && targetValue instanceof ArrayNode targetArray) {
                for (var item : sourceValue) {
                    targetArray.add(item.deepCopy());
                }
            } else {
                target.set(fieldName, sourceValue.deepCopy());
            }
        });
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

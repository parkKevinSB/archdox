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
    private static final String PHASE_CHECKLIST_GROUP_CODE = "PHASE_SUPERVISION";
    private static final String PHASE_CHECKLIST_GROUP_NAME = "단계별 감리업무";

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
        return get(catalogCode, siteId, siteId == null ? null : OfficeContext.requireCurrentOfficeId());
    }

    public JsonNode get(String catalogCode, Long siteId, Long officeId) {
        var normalized = normalize(catalogCode);
        var result = (ObjectNode) catalog(normalized).deepCopy();
        enrichSupervisionWorkMode(result, resolveSupervisionWorkMode(siteId, officeId));
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

        var atoms = catalog.path("canonicalAtoms");
        var trade = findByCode(catalog.path("trades"), normalizedTradeCode)
                .orElseThrow(() -> badSelection(
                        "SUPERVISION_CATALOG_TRADE_NOT_FOUND",
                        "Trade code is not defined in the supervision catalog",
                        normalizedTradeCode));
        var processGroup = atoms.path("processGroups").path(normalizedProcessCode);
        if (!processGroup.isObject() || !normalizedTradeCode.equals(normalize(processGroup.path("tradeCode").asText("")))) {
            throw badSelection(
                        "SUPERVISION_CATALOG_PROCESS_NOT_FOUND",
                        "Process code is not defined under the selected trade in the supervision catalog",
                        normalizedProcessCode);
        }
        var item = atoms.path("inspectionItems").path(normalizedInspectionItemCode);
        if (!item.isObject() || !normalizedProcessCode.equals(normalize(item.path("processGroupCode").asText("")))) {
            throw badSelection(
                        "SUPERVISION_CATALOG_INSPECTION_ITEM_NOT_FOUND",
                        "Inspection item code is not defined under the selected trade/process in the supervision catalog",
                        normalizedInspectionItemCode);
        }

        return new SupervisionCatalogSelection(
                "TRADE",
                text(catalog.path("catalogCode")),
                version(catalog),
                text(trade.path("tradeGroupCode")),
                text(trade.path("tradeGroupName")),
                "",
                "",
                normalizedTradeCode,
                text(trade.path("name")),
                firstNonBlank(text(processGroup.path("subTradeCode")), "NONE"),
                firstNonBlank(text(processGroup.path("subTradeName")), "없음"),
                "",
                "",
                normalizedProcessCode,
                text(processGroup.path("name")),
                normalizedInspectionItemCode,
                text(item.path("name")),
                text(item.path("basis")));
    }

    public SupervisionCatalogSelection requirePhaseInspectionItemSelection(
            String phaseCode,
            String processCode,
            String inspectionItemCode,
            Long siteId
    ) {
        var catalog = catalog(CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24);
        var normalizedPhaseCode = requiredCode(phaseCode, "phaseCode");
        var normalizedProcessCode = requiredCode(processCode, "processCode");
        var normalizedInspectionItemCode = requiredCode(inspectionItemCode, "inspectionItemCode");
        var modeCatalog = selectedModeCatalog(catalog.path("supervisionWorkModeCatalogs"), resolveSupervisionWorkMode(siteId, null));
        var phase = findPhaseRef(modeCatalog.path("phaseRefs"), normalizedPhaseCode)
                .orElseThrow(() -> badSelection(
                        "SUPERVISION_CATALOG_PHASE_NOT_FOUND",
                        "Construction phase code is not defined in the selected supervision catalog",
                        normalizedPhaseCode));
        var processGroupRef = findPhaseProcessRef(phase, normalizedProcessCode)
                .orElseThrow(() -> badSelection(
                        "SUPERVISION_CATALOG_PHASE_PROCESS_NOT_FOUND",
                        "Process code is not defined under the selected phase in the supervision catalog",
                        normalizedProcessCode));
        if (!containsText(processGroupRef.path("itemRefs"), normalizedInspectionItemCode)) {
            throw badSelection(
                    "SUPERVISION_CATALOG_PHASE_INSPECTION_ITEM_NOT_FOUND",
                    "Inspection item code is not defined under the selected phase/process in the supervision catalog",
                    normalizedInspectionItemCode);
        }
        var atoms = catalog.path("canonicalAtoms");
        var phaseAtom = atoms.path("constructionPhases").path(normalizedPhaseCode);
        var processAtom = atoms.path("processGroups").path(normalizedProcessCode);
        var item = atoms.path("inspectionItems").path(normalizedInspectionItemCode);
        return new SupervisionCatalogSelection(
                "PHASE",
                text(catalog.path("catalogCode")),
                version(catalog),
                "",
                "",
                PHASE_CHECKLIST_GROUP_CODE,
                PHASE_CHECKLIST_GROUP_NAME,
                "",
                "",
                "NONE",
                "없음",
                normalizedPhaseCode,
                text(phaseAtom.path("name")),
                normalizedProcessCode,
                text(processAtom.path("name")),
                normalizedInspectionItemCode,
                text(item.path("name")),
                text(item.path("basis")));
    }

    private JsonNode catalog(String normalized) {
        return catalogCache.computeIfAbsent(normalized, this::readCatalog);
    }

    private SupervisionWorkMode resolveSupervisionWorkMode(Long siteId, Long officeId) {
        if (siteId == null) {
            return SupervisionWorkMode.defaultMode();
        }
        if (siteRepository == null) {
            return SupervisionWorkMode.defaultMode();
        }
        var resolvedOfficeId = officeId == null ? OfficeContext.requireCurrentOfficeId() : officeId;
        return siteRepository.findByIdAndOfficeId(siteId, resolvedOfficeId)
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
        throw new IllegalStateException("Supervision work mode catalog is missing: " + mode.name());
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
            var merged = mergeCatalogParts(manifest);
            applyChecklistGroupProjections((ObjectNode) merged);
            return merged;
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

    private void applyChecklistGroupProjections(ObjectNode catalog) {
        applyTradeGroupMetadata(catalog);
        applyModeTradeGroupRefs(catalog);
        applyModePhaseChecklistGroupRefs(catalog);
    }

    private void applyTradeGroupMetadata(ObjectNode catalog) {
        var tradeGroups = catalog.path("canonicalAtoms").path("tradeGroups");
        for (var trade : catalog.withArray("/trades")) {
            if (trade instanceof ObjectNode tradeObject) {
                applyTradeGroupMetadata(tradeObject, tradeGroups);
            }
        }
        var tradeAtoms = catalog.path("canonicalAtoms").path("trades");
        if (tradeAtoms instanceof ObjectNode tradeAtomObjects) {
            tradeAtomObjects.fields().forEachRemaining(entry -> {
                if (entry.getValue() instanceof ObjectNode tradeObject) {
                    applyTradeGroupMetadata(tradeObject, tradeGroups);
                }
            });
        }
    }

    private void applyTradeGroupMetadata(ObjectNode trade, JsonNode tradeGroups) {
        var groupCode = firstNonBlank(text(trade.path("tradeGroupCode")), text(trade.path("discipline")));
        if (groupCode.isBlank()) {
            return;
        }
        trade.put("tradeGroupCode", groupCode);
        trade.put("tradeGroupName", text(tradeGroups.path(groupCode).path("name")));
    }

    private void applyModeTradeGroupRefs(ObjectNode catalog) {
        var modes = catalog.path("supervisionWorkModeCatalogs");
        if (!(modes instanceof ObjectNode modeObjects)) {
            return;
        }
        var tradesByCode = tradesByCode(catalog);
        modeObjects.fields().forEachRemaining(entry -> {
            if (!(entry.getValue() instanceof ObjectNode modeCatalog)) {
                return;
            }
            var tradeRefs = modeCatalog.path("tradeRefs");
            if (!tradeRefs.isArray() || tradeRefs.isEmpty()) {
                return;
            }
            var grouped = new LinkedHashMap<String, ArrayNode>();
            var groupNames = new LinkedHashMap<String, String>();
            for (var tradeRef : tradeRefs) {
                var tradeCode = text(tradeRef.path("tradeCode"));
                var trade = tradesByCode.get(tradeCode);
                var groupCode = trade == null ? "" : text(trade.path("tradeGroupCode"));
                if (groupCode.isBlank()) {
                    groupCode = "ARCHITECTURE";
                }
                grouped.computeIfAbsent(groupCode, ignored -> objectMapper.createArrayNode()).add(tradeRef.deepCopy());
                var groupName = trade == null ? "" : text(trade.path("tradeGroupName"));
                groupNames.putIfAbsent(groupCode, groupName);
            }
            var groupRefs = objectMapper.createArrayNode();
            grouped.forEach((groupCode, refs) -> {
                var groupRef = objectMapper.createObjectNode();
                groupRef.put("tradeGroupCode", groupCode);
                groupRef.put("tradeGroupName", groupNames.getOrDefault(groupCode, ""));
                groupRef.set("tradeRefs", refs);
                groupRefs.add(groupRef);
            });
            modeCatalog.set("tradeGroupRefs", groupRefs);
        });
    }

    private void applyModePhaseChecklistGroupRefs(ObjectNode catalog) {
        var modes = catalog.path("supervisionWorkModeCatalogs");
        if (!(modes instanceof ObjectNode modeObjects)) {
            return;
        }
        var configuredGroup = catalog.path("canonicalAtoms").path("phaseChecklistGroups").path(PHASE_CHECKLIST_GROUP_CODE);
        var groupName = firstNonBlank(text(configuredGroup.path("name")), PHASE_CHECKLIST_GROUP_NAME);
        modeObjects.fields().forEachRemaining(entry -> {
            if (!(entry.getValue() instanceof ObjectNode modeCatalog)) {
                return;
            }
            var phaseRefs = modeCatalog.path("phaseRefs");
            if (!phaseRefs.isArray() || phaseRefs.isEmpty()) {
                return;
            }
            var groupRef = objectMapper.createObjectNode();
            groupRef.put("phaseChecklistGroupCode", PHASE_CHECKLIST_GROUP_CODE);
            groupRef.put("phaseChecklistGroupName", groupName);
            groupRef.set("phaseRefs", phaseRefs.deepCopy());
            var groupRefs = objectMapper.createArrayNode();
            groupRefs.add(groupRef);
            modeCatalog.set("phaseChecklistGroupRefs", groupRefs);
        });
    }

    private Map<String, JsonNode> tradesByCode(JsonNode catalog) {
        var result = new LinkedHashMap<String, JsonNode>();
        for (var trade : catalog.path("trades")) {
            var code = text(trade.path("code"));
            if (!code.isBlank()) {
                result.put(code, trade);
            }
        }
        return result;
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

    private java.util.Optional<JsonNode> findPhaseRef(JsonNode phaseRefs, String phaseCode) {
        if (!phaseRefs.isArray()) {
            return java.util.Optional.empty();
        }
        for (var phaseRef : phaseRefs) {
            if (phaseCode.equals(normalize(phaseRef.path("phaseCode").asText("")))) {
                return java.util.Optional.of(phaseRef);
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<JsonNode> findPhaseProcessRef(JsonNode phaseRef, String processCode) {
        for (var workCategory : phaseRef.path("workCategories")) {
            for (var processGroupRef : workCategory.path("processGroupRefs")) {
                if (processCode.equals(normalize(processGroupRef.path("code").asText("")))) {
                    return java.util.Optional.of(processGroupRef);
                }
            }
        }
        return java.util.Optional.empty();
    }

    private boolean containsText(JsonNode values, String expected) {
        if (!values.isArray()) {
            return false;
        }
        for (var value : values) {
            if (expected.equals(normalize(value.asText("")))) {
                return true;
            }
        }
        return false;
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

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? (second == null ? "" : second.trim()) : first.trim();
    }

    public record SupervisionCatalogSelection(
            String groupType,
            String catalogCode,
            int catalogVersion,
            String tradeGroupCode,
            String tradeGroupName,
            String phaseChecklistGroupCode,
            String phaseChecklistGroupName,
            String tradeCode,
            String tradeName,
            String subTradeCode,
            String subTradeName,
            String phaseCode,
            String phaseName,
            String processCode,
            String processName,
            String inspectionItemCode,
            String inspectionItemName,
            String basis
    ) {
    }
}

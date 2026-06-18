package com.archdox.cloud.engine.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.supervisioncatalog.application.SupervisionDomainCatalogService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EngineCatalogBindingReviewService {
    private static final List<String> CATALOG_FIELDS = List.of(
            "tradeCode or phaseCode",
            "processCode",
            "inspectionItemCode");

    private final SupervisionDomainCatalogService catalogService;

    public EngineCatalogBindingReviewService(SupervisionDomainCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public EngineCatalogBindingReviewResult review(Map<String, Object> normalizedContext) {
        var catalogSelections = catalogSelections(normalizedContext);
        if (!catalogSelections.isEmpty()) {
            return reviewSelections(catalogSelections);
        }
        var tradeCode = contextValue(normalizedContext, "tradeCode");
        var phaseCode = contextValue(normalizedContext, "phaseCode");
        var processCode = contextValue(normalizedContext, "processCode");
        var inspectionItemCode = contextValue(normalizedContext, "inspectionItemCode");
        var hasAnyCatalogField = !isBlank(tradeCode) || !isBlank(phaseCode) || !isBlank(processCode) || !isBlank(inspectionItemCode);
        if (!hasAnyCatalogField) {
            return new EngineCatalogBindingReviewResult(List.of(), List.of(), Map.of(
                    "catalogReviewApplied", false,
                    "reason", "NO_CATALOG_FACTS"));
        }

        var missingFields = new ArrayList<String>();
        if (isBlank(tradeCode) && isBlank(phaseCode)) {
            missingFields.add("tradeCode or phaseCode");
        }
        if (isBlank(processCode)) {
            missingFields.add("processCode");
        }
        if (isBlank(inspectionItemCode)) {
            missingFields.add("inspectionItemCode");
        }
        if (!missingFields.isEmpty()) {
            return new EngineCatalogBindingReviewResult(
                    List.of(incompleteSelectionFinding(missingFields, tradeCode, phaseCode, processCode, inspectionItemCode, "context.catalogSelection")),
                    List.of(),
                    Map.of(
                            "catalogReviewApplied", true,
                            "catalogCode", catalogService.defaultConstructionCatalogCode(),
                            "catalogVersion", catalogService.defaultConstructionCatalogVersion(),
                            "missingCatalogFields", missingFields));
        }

        try {
            var selection = isBlank(phaseCode)
                    ? catalogService.requireInspectionItemSelection(tradeCode, processCode, inspectionItemCode)
                    : catalogService.requirePhaseInspectionItemSelection(phaseCode, processCode, inspectionItemCode, null);
            return new EngineCatalogBindingReviewResult(
                    List.of(),
                    List.of(selectionBinding(selection)),
                    Map.of(
                            "catalogReviewApplied", true,
                            "catalogCode", selection.catalogCode(),
                            "catalogVersion", selection.catalogVersion(),
                            "catalogBindingStatus", "MATCHED"));
        } catch (BadRequestException ex) {
            return new EngineCatalogBindingReviewResult(
                    List.of(invalidSelectionFinding(ex, tradeCode, phaseCode, processCode, inspectionItemCode, "context.catalogSelection")),
                    List.of(),
                    Map.of(
                            "catalogReviewApplied", true,
                            "catalogCode", catalogService.defaultConstructionCatalogCode(),
                            "catalogVersion", catalogService.defaultConstructionCatalogVersion(),
                            "catalogBindingStatus", "INVALID",
                            "errorCode", ex.code()));
        }
    }

    private EngineCatalogBindingReviewResult reviewSelections(List<Map<String, Object>> catalogSelections) {
        var findings = new ArrayList<ArchDoxEngineFinding>();
        var bindings = new ArrayList<Map<String, Object>>();
        var metadataSelections = new ArrayList<Map<String, Object>>();

        for (int index = 0; index < catalogSelections.size(); index++) {
            var selection = catalogSelections.get(index);
            var tradeCode = text(selection.get("tradeCode"));
            var phaseCode = text(selection.get("phaseCode"));
            var processCode = text(selection.get("processCode"));
            var inspectionItemCode = text(selection.get("inspectionItemCode"));
            var location = firstNonBlank(text(selection.get("location")), "context.catalogSelections[" + index + "]");
            var missingFields = new ArrayList<String>();
            if (isBlank(tradeCode) && isBlank(phaseCode)) {
                missingFields.add("tradeCode or phaseCode");
            }
            if (isBlank(processCode)) {
                missingFields.add("processCode");
            }
            if (isBlank(inspectionItemCode)) {
                missingFields.add("inspectionItemCode");
            }
            if (!missingFields.isEmpty()) {
                findings.add(incompleteSelectionFinding(missingFields, tradeCode, phaseCode, processCode, inspectionItemCode, location));
                metadataSelections.add(selectionMetadata(index, "INCOMPLETE", location, tradeCode, phaseCode, processCode, inspectionItemCode));
                continue;
            }

            try {
                var catalogSelection = isBlank(phaseCode)
                        ? catalogService.requireInspectionItemSelection(tradeCode, processCode, inspectionItemCode)
                        : catalogService.requirePhaseInspectionItemSelection(phaseCode, processCode, inspectionItemCode, null);
                var binding = new LinkedHashMap<>(selectionBinding(catalogSelection));
                binding.put("location", location);
                binding.put("selectionIndex", index);
                putIfPresent(binding, "sourceRef", text(selection.get("sourceRef")));
                putIfPresent(binding, "groupNo", text(selection.get("groupNo")));
                putIfPresent(binding, "entryNo", text(selection.get("entryNo")));
                bindings.add(Map.copyOf(binding));
                metadataSelections.add(selectionMetadata(index, "MATCHED", location, tradeCode, phaseCode, processCode, inspectionItemCode));
            } catch (BadRequestException ex) {
                findings.add(invalidSelectionFinding(ex, tradeCode, phaseCode, processCode, inspectionItemCode, location));
                metadataSelections.add(selectionMetadata(index, "INVALID", location, tradeCode, phaseCode, processCode, inspectionItemCode));
            }
        }

        return new EngineCatalogBindingReviewResult(
                List.copyOf(findings),
                List.copyOf(bindings),
                Map.of(
                        "catalogReviewApplied", true,
                        "catalogCode", catalogService.defaultConstructionCatalogCode(),
                        "catalogVersion", catalogService.defaultConstructionCatalogVersion(),
                        "catalogSelectionCount", catalogSelections.size(),
                        "catalogBindingCount", bindings.size(),
                        "catalogFindingCount", findings.size(),
                        "catalogSelections", List.copyOf(metadataSelections)));
    }

    private ArchDoxEngineFinding incompleteSelectionFinding(
            List<String> missingFields,
            String tradeCode,
            String phaseCode,
            String processCode,
            String inspectionItemCode,
            String location
    ) {
        return new ArchDoxEngineFinding(
                "CATALOG_SELECTION_INCOMPLETE",
                "COMPLETENESS",
                "MEDIUM",
                ArchDoxEngineFindingSource.DETERMINISTIC,
                location,
                "Construction supervision catalog selection is incomplete. Provide tradeCode, processCode, and inspectionItemCode together.",
                List.of(),
                metadata(
                        "DOMAIN_CATALOG_BINDING",
                        tradeCode,
                        phaseCode,
                        processCode,
                        inspectionItemCode,
                        Map.of("missingFields", missingFields)));
    }

    private ArchDoxEngineFinding invalidSelectionFinding(
            BadRequestException ex,
            String tradeCode,
            String phaseCode,
            String processCode,
            String inspectionItemCode,
            String location
    ) {
        return new ArchDoxEngineFinding(
                "CATALOG_SELECTION_INVALID",
                "DOMAIN_CATALOG",
                "HIGH",
                ArchDoxEngineFindingSource.DETERMINISTIC,
                location,
                "The supplied construction supervision catalog selection does not match the official ArchDox catalog.",
                List.of(),
                metadata(
                        "DOMAIN_CATALOG_BINDING",
                        tradeCode,
                        phaseCode,
                        processCode,
                        inspectionItemCode,
                        Map.of(
                                "errorCode", ex.code(),
                                "errorMessage", ex.getMessage())));
    }

    private Map<String, Object> selectionBinding(SupervisionDomainCatalogService.SupervisionCatalogSelection selection) {
        var binding = new LinkedHashMap<String, Object>();
        binding.put("groupType", selection.groupType());
        binding.put("catalogCode", selection.catalogCode());
        binding.put("catalogVersion", selection.catalogVersion());
        binding.put("tradeCode", selection.tradeCode());
        binding.put("tradeName", selection.tradeName());
        binding.put("phaseCode", selection.phaseCode());
        binding.put("phaseName", selection.phaseName());
        binding.put("processCode", selection.processCode());
        binding.put("processName", selection.processName());
        binding.put("inspectionItemCode", selection.inspectionItemCode());
        binding.put("inspectionItemName", selection.inspectionItemName());
        binding.put("basis", selection.basis());
        binding.put("reference", selection.catalogCode() + ":v" + selection.catalogVersion());
        return Map.copyOf(binding);
    }

    private Map<String, Object> metadata(
            String engineCheck,
            String tradeCode,
            String phaseCode,
            String processCode,
            String inspectionItemCode,
            Map<String, Object> extra
    ) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("engineCheck", engineCheck);
        metadata.put("catalogCode", catalogService.defaultConstructionCatalogCode());
        metadata.put("catalogVersion", catalogService.defaultConstructionCatalogVersion());
        metadata.put("requiredFields", CATALOG_FIELDS);
        metadata.put("tradeCode", blankToEmpty(tradeCode));
        metadata.put("phaseCode", blankToEmpty(phaseCode));
        metadata.put("processCode", blankToEmpty(processCode));
        metadata.put("inspectionItemCode", blankToEmpty(inspectionItemCode));
        if (extra != null) {
            metadata.putAll(extra);
        }
        return Map.copyOf(metadata);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> catalogSelections(Map<String, Object> normalizedContext) {
        if (normalizedContext == null) {
            return List.of();
        }
        var rawSelections = normalizedContext.get("catalogSelections");
        if (!(rawSelections instanceof List<?> selections)) {
            return List.of();
        }
        return selections.stream()
                .filter(Map.class::isInstance)
                .map(selection -> (Map<String, Object>) selection)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private String contextValue(Map<String, Object> normalizedContext, String fieldName) {
        if (normalizedContext == null) {
            return "";
        }
        var values = normalizedContext.get("values");
        if (!(values instanceof Map<?, ?> rawValues)) {
            return "";
        }
        var rawValue = rawValues.get(fieldName);
        if (!(rawValue instanceof Map<?, ?> rawMap)) {
            return "";
        }
        var valueMap = (Map<String, Object>) rawMap;
        var canonical = text(valueMap.get("canonicalValue"));
        return canonical.isBlank() ? text(valueMap.get("rawValue")) : canonical;
    }

    private String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private Map<String, Object> selectionMetadata(
            int index,
            String status,
            String location,
            String tradeCode,
            String phaseCode,
            String processCode,
            String inspectionItemCode
    ) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("selectionIndex", index);
        metadata.put("status", status);
        metadata.put("location", location);
        metadata.put("tradeCode", blankToEmpty(tradeCode));
        metadata.put("phaseCode", blankToEmpty(phaseCode));
        metadata.put("processCode", blankToEmpty(processCode));
        metadata.put("inspectionItemCode", blankToEmpty(inspectionItemCode));
        return Map.copyOf(metadata);
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record EngineCatalogBindingReviewResult(
            List<ArchDoxEngineFinding> findings,
            List<Map<String, Object>> catalogBindings,
            Map<String, Object> metadata
    ) {
        public EngineCatalogBindingReviewResult {
            findings = findings == null ? List.of() : List.copyOf(findings);
            catalogBindings = catalogBindings == null ? List.of() : List.copyOf(catalogBindings);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}

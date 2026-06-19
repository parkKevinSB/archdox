package com.archdox.cloud.supervisioncatalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupervisionDomainCatalogResourceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SupervisionDomainCatalogService catalogService = new SupervisionDomainCatalogService(objectMapper);

    @Test
    void workModeRefsPointToCanonicalAtoms() throws Exception {
        var catalog = readCatalog();
        var atoms = catalog.path("canonicalAtoms");

        assertThat(atoms.path("trades").path("REINFORCED_CONCRETE").path("name").asText())
                .isEqualTo("철근 콘크리트 공사");

        var modes = catalog.path("supervisionWorkModeCatalogs");
        assertThat(atoms.path("tradeGroups").path("ARCHITECTURE").path("code").asText())
                .isEqualTo("ARCHITECTURE");
        assertThat(atoms.path("phaseChecklistGroups").path("PHASE_SUPERVISION").path("code").asText())
                .isEqualTo("PHASE_SUPERVISION");
        assertThat(modes.path("NON_RESIDENT").path("tradeRefs").path(0).path("tradeCode").asText())
                .isEqualTo("REINFORCED_CONCRETE");
        assertThat(modes.path("NON_RESIDENT").path("tradeGroupRefs").path(0).path("tradeGroupCode").asText())
                .isEqualTo("ARCHITECTURE");
        assertThat(modes.path("NON_RESIDENT").path("tradeGroupRefs").path(0).path("tradeRefs").path(0).path("tradeCode").asText())
                .isEqualTo("REINFORCED_CONCRETE");
        assertThat(modes.path("RESIDENT").path("tradeRefs").path(0).path("sourcePages").path(0).asInt())
                .isEqualTo(79);
        assertThat(modes.path("RESPONSIBLE_RESIDENT").path("tradeRefs").path(0).path("sourcePages").path(0).asInt())
                .isEqualTo(132);
        assertThat(modes.path("NON_RESIDENT").path("phaseRefs").path(0).path("phaseCode").asText())
                .isEqualTo("PRE_CONSTRUCTION");
        assertThat(modes.path("NON_RESIDENT").path("phaseChecklistGroupRefs").path(0).path("phaseChecklistGroupCode").asText())
                .isEqualTo("PHASE_SUPERVISION");
        assertThat(modes.path("NON_RESIDENT").path("phaseChecklistGroupRefs").path(0).path("phaseRefs").path(0).path("phaseCode").asText())
                .isEqualTo("PRE_CONSTRUCTION");
        assertThat(modes.path("NON_RESIDENT").path("phaseRefs").size()).isEqualTo(3);
        assertThat(modes.path("RESIDENT").path("phaseRefs").size()).isEqualTo(3);
        assertThat(modes.path("RESPONSIBLE_RESIDENT").path("phaseRefs").size()).isEqualTo(3);
        assertThat(atoms.path("constructionPhases").size()).isEqualTo(3);
        assertThat(atoms.path("inspectionItems").size()).isGreaterThan(250);

        assertModeRefsExist(modes, atoms);
        assertPhaseRefsExist(modes, atoms);
        assertItemRowRefsExist(atoms);
    }

    @Test
    void phaseInspectionItemSelectionResolvesFromSelectedModeCatalog() {
        var selection = catalogService.requirePhaseInspectionItemSelection(
                "PRE_CONSTRUCTION",
                "PHASE_NON_RESIDENT_PRE_CONSTRUCTION_BASIC_PG_FC0767BB28",
                "PHASE_NON_RESIDENT_PRE_CONSTRUCTION_BASIC_IT_3C8CC2930A",
                null);

        assertThat(selection.groupType()).isEqualTo("PHASE");
        assertThat(selection.phaseChecklistGroupCode()).isEqualTo("PHASE_SUPERVISION");
        assertThat(selection.phaseCode()).isEqualTo("PRE_CONSTRUCTION");
        assertThat(selection.phaseName()).isEqualTo("공사전 단계");
        assertThat(selection.processName()).isEqualTo("감리업무 착수준비");
        assertThat(selection.inspectionItemName()).isEqualTo("당해 공사 관련 설계도서 인수 확인서 작성");
        assertThat(selection.tradeCode()).isEmpty();
    }

    @Test
    void tradeInspectionItemSelectionIncludesTradeGroup() {
        var selection = catalogService.requireInspectionItemSelection(
                "REINFORCED_CONCRETE",
                "REBAR_ASSEMBLY",
                "RC_REBAR_CONFIRMATION");

        assertThat(selection.groupType()).isEqualTo("TRADE");
        assertThat(selection.tradeGroupCode()).isEqualTo("ARCHITECTURE");
        assertThat(selection.tradeCode()).isEqualTo("REINFORCED_CONCRETE");
        assertThat(selection.processCode()).isEqualTo("REBAR_ASSEMBLY");
        assertThat(selection.inspectionItemCode()).isEqualTo("RC_REBAR_CONFIRMATION");
        assertThat(selection.phaseChecklistGroupCode()).isEmpty();
    }

    private void assertModeRefsExist(JsonNode modes, JsonNode atoms) {
        for (Iterator<Map.Entry<String, JsonNode>> modeIterator = modes.fields(); modeIterator.hasNext(); ) {
            var mode = modeIterator.next();
            for (var tradeRef : mode.getValue().path("tradeRefs")) {
                var tradeCode = tradeRef.path("tradeCode").asText();
                assertThat(atoms.path("trades").path(tradeCode).isObject())
                        .as(mode.getKey() + " tradeRef " + tradeCode)
                        .isTrue();

                for (var workCategory : tradeRef.path("workCategories")) {
                    for (var processGroupRef : workCategory.path("processGroupRefs")) {
                        var processGroupCode = processGroupRef.path("code").asText();
                        assertThat(atoms.path("processGroups").path(processGroupCode).isObject())
                                .as(mode.getKey() + " processGroupRef " + processGroupCode)
                                .isTrue();
                        for (var itemRef : processGroupRef.path("itemRefs")) {
                            var itemCode = itemRef.asText();
                            assertThat(atoms.path("inspectionItems").path(itemCode).isObject())
                                    .as(mode.getKey() + " itemRef " + itemCode)
                                    .isTrue();
                        }
                    }
                }
            }
        }
    }

    private void assertPhaseRefsExist(JsonNode modes, JsonNode atoms) {
        for (Iterator<Map.Entry<String, JsonNode>> modeIterator = modes.fields(); modeIterator.hasNext(); ) {
            var mode = modeIterator.next();
            for (var phaseRef : mode.getValue().path("phaseRefs")) {
                var phaseCode = phaseRef.path("phaseCode").asText();
                assertThat(atoms.path("constructionPhases").path(phaseCode).isObject())
                        .as(mode.getKey() + " phaseRef " + phaseCode)
                        .isTrue();

                for (var workCategory : phaseRef.path("workCategories")) {
                    for (var processGroupRef : workCategory.path("processGroupRefs")) {
                        var processGroupCode = processGroupRef.path("code").asText();
                        assertThat(atoms.path("processGroups").path(processGroupCode).isObject())
                                .as(mode.getKey() + " phase processGroupRef " + processGroupCode)
                                .isTrue();
                        for (var itemRef : processGroupRef.path("itemRefs")) {
                            var itemCode = itemRef.asText();
                            assertThat(atoms.path("inspectionItems").path(itemCode).isObject())
                                    .as(mode.getKey() + " phase itemRef " + itemCode)
                                    .isTrue();
                        }
                    }
                }
            }
        }
    }

    private void assertItemRowRefsExist(JsonNode atoms) {
        var items = atoms.path("inspectionItems");
        for (Iterator<Map.Entry<String, JsonNode>> itemIterator = items.fields(); itemIterator.hasNext(); ) {
            var item = itemIterator.next();
            for (var rowRef : item.getValue().path("rowRefs")) {
                var rowCode = rowRef.asText();
                assertThat(atoms.path("checklistRows").path(rowCode).isObject())
                        .as(item.getKey() + " rowRef " + rowCode)
                        .isTrue();
            }
        }
    }

    private JsonNode readCatalog() {
        return catalogService.get(SupervisionDomainCatalogService.CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24);
    }
}

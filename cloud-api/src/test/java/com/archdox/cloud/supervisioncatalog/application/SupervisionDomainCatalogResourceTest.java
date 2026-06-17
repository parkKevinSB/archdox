package com.archdox.cloud.supervisioncatalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SupervisionDomainCatalogResourceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void workModeRefsPointToCanonicalAtoms() throws Exception {
        var catalog = readCatalog();
        var atoms = catalog.path("canonicalAtoms");

        assertThat(atoms.path("trades").path("REINFORCED_CONCRETE").path("name").asText())
                .isEqualTo("철근 콘크리트 공사");

        var modes = catalog.path("supervisionWorkModeCatalogs");
        assertThat(modes.path("NON_RESIDENT").path("tradeRefs").path(0).path("tradeCode").asText())
                .isEqualTo("REINFORCED_CONCRETE");
        assertThat(modes.path("RESIDENT").path("tradeRefs").path(0).path("sourcePages").path(0).asInt())
                .isEqualTo(79);
        assertThat(modes.path("RESPONSIBLE_RESIDENT").path("tradeRefs").path(0).path("sourcePages").path(0).asInt())
                .isEqualTo(132);

        assertModeRefsExist(modes, atoms);
        assertItemRowRefsExist(atoms);
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

    private JsonNode readCatalog() throws IOException {
        var resource = new ClassPathResource(
                "domain-catalogs/construction-supervision-checklist-2020-12-24.json");
        try (var input = resource.getInputStream()) {
            return objectMapper.readTree(input);
        }
    }
}

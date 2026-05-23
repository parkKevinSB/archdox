package com.archdox.cloud.document.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateBindingResolverTest {
    private final TemplateBindingResolver resolver = new TemplateBindingResolver();

    @Test
    void resolvesBindingsFromNeutralSnapshotPaths() {
        var snapshot = Map.<String, Object>of(
                "project", Map.of("name", "Document Tower"),
                "steps", Map.of(
                        "BASIC_INFO", Map.of(
                                "payload", Map.of(
                                        "inspectionDate", "2026-05-23",
                                        "inspectorName", "Kim"))));
        var schema = Map.<String, Object>of(
                "bindings", Map.of(
                        "projectName", "project.name",
                        "inspectionDate", "steps.BASIC_INFO.payload.inspectionDate"),
                "fields", List.of(Map.of(
                        "key", "inspectorName",
                        "source", "steps.BASIC_INFO.payload.inspectorName")));

        var fields = resolver.resolve(schema, snapshot);

        assertEquals("Document Tower", fields.get("projectName"));
        assertEquals("2026-05-23", fields.get("inspectionDate"));
        assertEquals("Kim", fields.get("inspectorName"));
    }

    @Test
    void missingPathBecomesBlankField() {
        var fields = resolver.resolve(
                Map.of("bindings", Map.of("missing", "project.address")),
                Map.of("project", Map.of("name", "Document Tower")));

        assertEquals("", fields.get("missing"));
    }
}

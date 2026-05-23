package com.archdox.document;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceFormSpecTest {
    private static final List<String> SPECS = List.of(
            "reference-form-specs/korean-construction-supervision-report-appendix-1.json",
            "reference-form-specs/korean-construction-daily-supervision-log-appendix-2.json",
            "reference-form-specs/korean-demolition-safety-checklist-appendix-1.json",
            "reference-form-specs/korean-demolition-daily-supervision-log-appendix-2.json",
            "reference-form-specs/korean-demolition-completion-report-appendix-3.json"
    );

    @Test
    void koreanReferenceFormSpecsAreAvailableToDocumentEngineTests() throws Exception {
        for (String spec : SPECS) {
            var content = readResource(spec);
            assertTrue(content.contains("\"code\""), spec);
            assertTrue(content.contains("\"referencePdf\""), spec);
            assertTrue(content.contains("\"recommendedPlaceholders\""), spec);
            assertTrue(content.contains("\"sections\""), spec);
        }
    }

    @Test
    void dailySupervisionSpecDocumentsCoreBindingTargets() throws Exception {
        var content = readResource("reference-form-specs/korean-construction-daily-supervision-log-appendix-2.json");

        assertTrue(content.contains("\"KOREAN_CONSTRUCTION_DAILY_SUPERVISION_APPENDIX_2\""));
        assertTrue(content.contains("\"constructionName\""));
        assertTrue(content.contains("\"inspectionDate\""));
        assertTrue(content.contains("\"CHECKLIST_TABLE\""));
    }

    private String readResource(String path) throws Exception {
        var resource = getClass().getClassLoader().getResource(path);
        assertNotNull(resource, path);
        try (var input = resource.openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

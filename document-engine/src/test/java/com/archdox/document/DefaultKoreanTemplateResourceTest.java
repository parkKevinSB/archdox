package com.archdox.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class DefaultKoreanTemplateResourceTest {
    private static final List<TemplateCase> TEMPLATE_CASES = List.of(
            new TemplateCase(
                    "KOREAN_CONSTRUCTION_SUPERVISION_REPORT_APPENDIX_1",
                    "templates/korean/korean-construction-supervision-report-appendix-1.docx",
                    List.of("reportOpinionSection"),
                    List.of("Project Alpha", "PERMIT-001", "Sample field note")),
            new TemplateCase(
                    "KOREAN_CONSTRUCTION_DAILY_SUPERVISION_LOG_APPENDIX_2",
                    "templates/korean/korean-construction-daily-supervision-log-appendix-2.docx",
                    List.of("supervisionItemsSection"),
                    List.of("Project Alpha", "2026-05-23", "Sample field note")),
            new TemplateCase(
                    "KOREAN_DEMOLITION_SAFETY_CHECKLIST_APPENDIX_1",
                    "templates/korean/korean-demolition-safety-checklist-appendix-1.docx",
                    List.of("safetyChecklistSection"),
                    List.of("2026-05-23", "Zone 1", "No critical issue")),
            new TemplateCase(
                    "KOREAN_DEMOLITION_DAILY_SUPERVISION_LOG_APPENDIX_2",
                    "templates/korean/korean-demolition-daily-supervision-log-appendix-2.docx",
                    List.of("supervisionItemsSection"),
                    List.of("Project Alpha", "2026-05-23", "Sample field note")),
            new TemplateCase(
                    "KOREAN_DEMOLITION_COMPLETION_REPORT_APPENDIX_3",
                    "templates/korean/korean-demolition-completion-report-appendix-3.docx",
                    List.of("supervisorDeploymentSection"),
                    List.of("Demolition supervision", "Seoul Test Address", "Acceptable with notes"))
    );

    @Test
    void bundledKoreanDefaultTemplatesRenderWithoutUnresolvedPlaceholders() throws Exception {
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.ofNullable(resourceBytes(spec.storageRef())),
                new SimpleDocumentEngine());

        for (TemplateCase templateCase : TEMPLATE_CASES) {
            var result = engine.generate(new DocumentGenerationRequest(
                    "job-" + templateCase.code().toLowerCase(),
                    "office-default",
                    "report-default",
                    new TemplateSpec(templateCase.code(), 1, templateCase.storageRef(), "{}", "{}", null, true),
                    payload(templateCase.richSections()),
                    List.of(),
                    OutputFormat.DOCX));

            assertEquals(GenerationStatus.COMPLETED, result.status(), templateCase.code());
            assertEquals(1, result.artifacts().size(), templateCase.code());
            assertEquals(ArtifactType.DOCX, result.artifacts().get(0).type(), templateCase.code());

            var documentXml = zipEntry(result.artifacts().get(0).content(), "word/document.xml");
            for (String expectedValue : templateCase.expectedValues()) {
                assertTrue(documentXml.contains(expectedValue), templateCase.code() + " missing " + expectedValue);
            }
            assertTrue(documentXml.contains("CHK-001"), templateCase.code());
            assertTrue(documentXml.contains("Checked item"), templateCase.code());
            assertTrue(documentXml.contains("Meets criteria"), templateCase.code());
            assertFalse(documentXml.contains("${"), templateCase.code());

            writeDefaultTemplateArtifact(templateCase, result.artifacts().get(0).content());
        }
    }

    @Test
    void koreanDefaultTemplateCatalogIsBundledWithTemplateStorageRefs() throws Exception {
        var catalog = new String(resourceBytes("templates/korean/catalog.json"), StandardCharsets.UTF_8);

        assertTrue(catalog.contains("\"version\""));
        for (TemplateCase templateCase : TEMPLATE_CASES) {
            assertTrue(catalog.contains(templateCase.code()), templateCase.code());
            assertTrue(catalog.contains(templateCase.storageRef()), templateCase.storageRef());
        }
    }

    @Test
    void bundledDocumentTemplatesResolveClasspathStorageRefs() throws Exception {
        for (TemplateCase templateCase : TEMPLATE_CASES) {
            var content = BundledDocumentTemplates.read(templateCase.storageRef());
            assertTrue(content.isPresent(), templateCase.storageRef());
            assertTrue(content.get().length > 0, templateCase.storageRef());
        }

        assertTrue(BundledDocumentTemplates.read("missing.docx").isEmpty());
    }

    private Map<String, Object> payload(List<String> richSections) {
        var sections = new LinkedHashMap<String, Object>();
        for (String richSection : richSections) {
            sections.put(richSection, checklistSection());
        }

        return Map.of(
                "templateFields", templateFields(),
                "layoutSections", sections,
                "checklistAnswers", List.of(
                        Map.of(
                                "itemCode", "CHK-001",
                                "label", "Checked item",
                                "answer", Map.of(
                                        "value", "OK",
                                        "trade", "Concrete",
                                        "criteria", "Reference criteria"),
                                "note", "Meets criteria"),
                        Map.of(
                                "itemCode", "CHK-002",
                                "label", "Follow up item",
                                "answer", Map.of(
                                        "value", "NEEDS_ACTION",
                                        "trade", "Temporary work",
                                        "criteria", "Safety criteria"),
                                "note", "Sample field note")));
    }

    private Map<String, Object> checklistSection() {
        return Map.of(
                "type", "CHECKLIST_TABLE",
                "includeTitle", false,
                "tableStyle", "ArchDoxInspectionTable",
                "headerFill", "E8EEF5",
                "borderColor", "5E6A75",
                "fields", List.of(
                        Map.of("label", "Code", "source", "itemCode", "width", 1400),
                        Map.of("label", "Trade", "source", "answer.trade", "width", 2100),
                        Map.of("label", "Item", "source", "label", "width", 2600),
                        Map.of("label", "Result", "source", "answer.value", "width", 1500),
                        Map.of("label", "Note", "source", "note", "width", 2400)));
    }

    private Map<String, Object> templateFields() {
        return Map.ofEntries(
                Map.entry("serialNo", "SN-2026-0001"),
                Map.entry("chiefSupervisorName", "Kim Supervisor"),
                Map.entry("architectAssistantName", "Park Architect"),
                Map.entry("constructionName", "Project Alpha"),
                Map.entry("inspectionDate", "2026-05-23"),
                Map.entry("inspectionDayOfWeek", "Saturday"),
                Map.entry("weather", "Clear"),
                Map.entry("siteName", "Site A"),
                Map.entry("specialNotes", "Sample field note"),
                Map.entry("issueAndAction", "Action completed"),
                Map.entry("permitNumber", "PERMIT-001"),
                Map.entry("permitDate", "2026-04-01"),
                Map.entry("siteAddress", "Seoul Test Address"),
                Map.entry("lotNumber", "Lot 1"),
                Map.entry("buildingName", "Building A"),
                Map.entry("progressType", "Intermediate"),
                Map.entry("supervisionStartDate", "2026-04-01"),
                Map.entry("supervisionEndDate", "2026-05-23"),
                Map.entry("completionDate", "2026-05-24"),
                Map.entry("relationEngineerOpinion", "Reviewed"),
                Map.entry("comprehensiveOpinion", "Acceptable with notes"),
                Map.entry("reportDate", "2026-05-24"),
                Map.entry("ownerName", "Owner A"),
                Map.entry("fieldInvestigationSummary", "Field investigation completed"),
                Map.entry("structureStatus", "Structure checked"),
                Map.entry("evacuationStatus", "Evacuation path checked"),
                Map.entry("fireSafetyStatus", "Fire safety checked"),
                Map.entry("zoningStatus", "Zoning checked"),
                Map.entry("safetyInspectionDate", "2026-05-23"),
                Map.entry("inspectionLocation", "Zone 1"),
                Map.entry("supervisorName", "Lee Supervisor"),
                Map.entry("demolitionWorkerName", "Worker A"),
                Map.entry("safetyCheckStage", "Before work"),
                Map.entry("correctiveAction", "No critical issue"),
                Map.entry("inspectorName", "Inspector A"),
                Map.entry("workDescription", "Daily inspection"),
                Map.entry("supervisorOfficeName", "ArchDox Office"),
                Map.entry("supervisorLicenseNumber", "LIC-001"),
                Map.entry("supervisorAddress", "Office Address"),
                Map.entry("supervisorPhone", "010-0000-0000"),
                Map.entry("contractorName", "Contractor A"),
                Map.entry("contractorOfficeName", "Contractor Office"),
                Map.entry("contractorLicenseNumber", "CON-001"),
                Map.entry("contractorAddress", "Contractor Address"),
                Map.entry("contractorPhone", "010-1111-1111"),
                Map.entry("serviceName", "Demolition supervision"),
                Map.entry("serviceOverview", "Supervision service overview"),
                Map.entry("constructionStartDate", "2026-04-01"),
                Map.entry("constructionEndDate", "2026-05-23"),
                Map.entry("constructionAmount", "100000"),
                Map.entry("supervisionAmount", "10000"));
    }

    private byte[] resourceBytes(String path) throws IOException {
        var resource = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(resource, path);
        try (resource) {
            return resource.readAllBytes();
        }
    }

    private String zipEntry(byte[] content, String path) throws IOException {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (path.equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException(path + " not found in docx");
    }

    private void writeDefaultTemplateArtifact(TemplateCase templateCase, byte[] content) throws IOException {
        var outputDir = Path.of("build", "archdox-default-templates");
        Files.createDirectories(outputDir);
        Files.write(outputDir.resolve(templateCase.code().toLowerCase() + ".docx"), content);
    }

    private record TemplateCase(
            String code,
            String storageRef,
            List<String> richSections,
            List<String> expectedValues
    ) {
    }
}

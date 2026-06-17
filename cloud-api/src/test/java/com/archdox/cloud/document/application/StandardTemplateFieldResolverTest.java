package com.archdox.cloud.document.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StandardTemplateFieldResolverTest {
    private final StandardTemplateFieldResolver resolver = new StandardTemplateFieldResolver();

    @Test
    void resolvesConstructionDailySupervisionFieldsFromNeutralSnapshot() {
        var fields = resolver.resolve(Map.of(
                "report", Map.of(
                        "reportNo", "R-001",
                        "reportType", "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                        "title", "Daily log"),
                "project", Map.of(
                        "name", "Document Tower",
                        "address", "Seoul",
                        "buildingType", "Office"),
                "site", Map.of(
                        "name", "North Site",
                        "address", "Gangnam",
                        "siteCode", "N-1"),
                "steps", Map.of(
                        "BASIC_INFO", Map.of("payload", Map.of(
                                "inspectionDate", "2026-05-23",
                                "weather", "Clear",
                                "inspectorName", "Kim",
                                "supervisorName", "Lee")),
                        "DAILY_LOG", Map.of("payload", Map.of(
                                "dailyItems", Map.of(
                                        "groups", List.of(Map.of(
                                                "tradeCode", "REINFORCED_CONCRETE",
                                                "tradeName", "Concrete",
                                                "processCode", "REBAR_ASSEMBLY",
                                                "processName", "Slab",
                                                "floor", "3F",
                                                "entries", List.of(Map.of(
                                                        "inspectionItemCode", "RC_REBAR_CONFIRMATION",
                                                        "inspectionItemName", "Rebar confirmation",
                                                        "documentNarrativeText", "Polished rebar narrative for generated document.",
                                                        "checklistRows", List.of(Map.of(
                                                                "label", "Checked rebar",
                                                                "result", "COMPLIANT",
                                                                "referenceNote", "",
                                                                "actionNote", "")),
                                                        "photoIds", List.of()))))),
                                "specialNotes", "No special issue",
                                "issueAndAction", "None"))),
                "checklistAnswers", List.of()));

        assertEquals("Daily log", fields.get("documentTitle"));
        assertEquals("Document Tower", fields.get("constructionName"));
        assertEquals("North Site", fields.get("siteName"));
        assertEquals("Gangnam", fields.get("siteAddress"));
        assertEquals("2026-05-23", fields.get("inspectionDate"));
        assertEquals("2026", fields.get("inspectionYear"));
        assertEquals("5", fields.get("inspectionMonth"));
        assertEquals("23", fields.get("inspectionDay"));
        assertEquals("\uD1A0", fields.get("inspectionDayOfWeek"));
        assertEquals("Clear", fields.get("weather"));
        assertEquals("Lee", fields.get("supervisorName"));
        assertEquals("Kim", fields.get("inspectorName"));
        assertEquals("Concrete", fields.get("constructionTrade"));
        assertEquals("Slab", fields.get("detailedProcess"));
        assertEquals("3F", fields.get("floor"));
        assertEquals("Rebar confirmation", fields.get("inspectionItem"));
        assertEquals("Polished rebar narrative for generated document.", fields.get("supervisionContent"));
        assertEquals("None", fields.get("issueAndAction"));
        assertEquals("None", fields.get("correctionResults"));
    }

    @Test
    void resolvesConstructionSupervisionReportFields() {
        var fields = resolver.resolve(Map.of(
                "report", Map.of("reportType", "CONSTRUCTION_SUPERVISION_REPORT", "title", ""),
                "project", Map.of("name", "Construction Project"),
                "site", Map.of("name", "Main Site", "address", "Busan"),
                "steps", Map.of(
                        "BASIC_INFO", Map.of("payload", Map.of(
                                "permitNumber", "2026-ARCH-01",
                                "permitDate", "2026-05-01",
                                "lotNumber", "353-1",
                                "supervisionStartDate", "2026-05-02",
                                "supervisionEndDate", "2026-06-02",
                                "supervisorName", "Lee")),
                        "REMARKS", Map.of("payload", Map.of(
                                "relationEngineerOpinion", "Reviewed",
                                "comprehensiveOpinion", "Acceptable",
                                "specialNotes", "No special issue")))));

        assertEquals("\uAC10\uB9AC\uBCF4\uACE0\uC11C", fields.get("documentTitle"));
        assertEquals("2026-ARCH-01", fields.get("permitNumber"));
        assertEquals("2026-05-01", fields.get("permitDate"));
        assertEquals("353-1", fields.get("lotNumber"));
        assertEquals("2026-05-02", fields.get("supervisionStartDate"));
        assertEquals("2026-06-02", fields.get("supervisionEndDate"));
        assertEquals("Lee", fields.get("supervisorName"));
        assertEquals("Reviewed", fields.get("relationEngineerOpinion"));
        assertEquals("Acceptable", fields.get("comprehensiveOpinion"));
        assertEquals("No special issue", fields.get("specialNotes"));
    }

    @Test
    void resolvesDailyLogRemarksFieldsFromRemarksStep() {
        var fields = resolver.resolve(Map.of(
                "report", Map.of("reportType", "CONSTRUCTION_DAILY_SUPERVISION_LOG", "title", "Daily log"),
                "steps", Map.of(
                        "REMARKS", Map.of("payload", Map.of(
                                "remarks", "Special site memo",
                                "issueAndAction", "Guardrail reinforced and verified",
                                "nextAction", "Reinspect before concrete pour")))));

        assertEquals("Special site memo", fields.get("specialNotes"));
        assertEquals("Guardrail reinforced and verified", fields.get("issueAndAction"));
        assertEquals("Guardrail reinforced and verified", fields.get("correctionResults"));
        assertEquals("Reinspect before concrete pour", fields.get("nextAction"));
    }
}

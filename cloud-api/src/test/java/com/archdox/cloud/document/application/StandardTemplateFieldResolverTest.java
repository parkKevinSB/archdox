package com.archdox.cloud.document.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                        "reportType", "DAILY_SUPERVISION",
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
                                "constructionTrade", "Concrete",
                                "detailedProcess", "Slab",
                                "floor", "3F",
                                "supervisionContent", "Checked rebar",
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
        assertEquals("Checked rebar", fields.get("supervisionContent"));
        assertEquals("None", fields.get("issueAndAction"));
        assertEquals("None", fields.get("correctionResults"));
    }

    @Test
    void resolvesDemolitionSafetyCheckFields() {
        var fields = resolver.resolve(Map.of(
                "report", Map.of("reportType", "DEMOLITION_SAFETY_CHECK", "title", ""),
                "project", Map.of("name", "Demolition Project"),
                "site", Map.of("name", "Old Wing", "address", "Busan"),
                "steps", Map.of(
                        "DEMOLITION_SAFETY_CHECK", Map.of("payload", Map.of(
                                "inspectionDate", "2026-05-22",
                                "location", "Roof",
                                "stage", "Roof demolition",
                                "demolitionWorkerName", "Park",
                                "inspectionCriteria", "Jack support spacing",
                                "inspectionResult", "Pass",
                                "correctiveAction", "Tighten supports")))));

        assertEquals("\uD574\uCCB4\uACF5\uC0AC \uC548\uC804\uC810\uAC80\uD45C", fields.get("documentTitle"));
        assertEquals("2026-05-22", fields.get("safetyInspectionDate"));
        assertEquals("Roof", fields.get("inspectionLocation"));
        assertEquals("Roof demolition", fields.get("demolitionWorkStage"));
        assertEquals("Park", fields.get("demolitionWorkerName"));
        assertEquals("Jack support spacing", fields.get("inspectionCriteria"));
        assertEquals("Pass", fields.get("inspectionResult"));
        assertEquals("Tighten supports", fields.get("correctiveAction"));
    }

    @Test
    void resolvesCompletionAndTemplateAliasFields() {
        var fields = resolver.resolve(Map.of(
                "report", Map.of("reportType", "DEMOLITION_COMPLETION_REPORT", "title", ""),
                "project", Map.of("name", "Demolition Project"),
                "steps", Map.of(
                        "BASIC_INFO", Map.of("payload", Map.of(
                                "architectAssistantName", "Park Architect",
                                "supervisorOfficeName", "Arch Office",
                                "contractorName", "Builder Co.",
                                "serviceName", "Completion service",
                                "reportDate", "2026-05-24")),
                        "REMARKS", Map.of("payload", Map.of(
                                "relationEngineerOpinion", "Reviewed",
                                "comprehensiveOpinion", "Acceptable")))));

        assertEquals("Park Architect", fields.get("assistantArchitectName"));
        assertEquals("Arch Office", fields.get("supervisorOfficeName"));
        assertEquals("Builder Co.", fields.get("contractorName"));
        assertEquals("Completion service", fields.get("serviceName"));
        assertEquals("2026-05-24", fields.get("reportDate"));
        assertEquals("Reviewed", fields.get("relationEngineerOpinion"));
        assertEquals("Acceptable", fields.get("comprehensiveOpinion"));
    }
}

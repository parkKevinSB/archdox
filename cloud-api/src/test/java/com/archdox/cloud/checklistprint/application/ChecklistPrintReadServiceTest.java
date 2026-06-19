package com.archdox.cloud.checklistprint.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.domain.PayloadStorageMode;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.site.infra.SiteRepository;
import com.archdox.cloud.supervisioncatalog.application.SupervisionDomainCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ChecklistPrintReadServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InspectionReportRepository reportRepository = mock(InspectionReportRepository.class);
    private final InspectionReportStepRepository stepRepository = mock(InspectionReportStepRepository.class);
    private final OfficePermissionService permissionService = mock(OfficePermissionService.class);
    private final SupervisionDomainCatalogService catalogService = new SupervisionDomainCatalogService(objectMapper);
    private final ChecklistPrintReadService service = new ChecklistPrintReadService(
            reportRepository,
            stepRepository,
            permissionService,
            catalogService,
            objectMapper);

    @AfterEach
    void clearOfficeContext() {
        OfficeContext.clear();
    }

    @Test
    void tradePreviewExpandsWholeTradeChecklistWhenOneRowIsChecked() {
        OfficeContext.set(10L);
        var now = OffsetDateTime.parse("2026-06-19T10:00:00+09:00");
        var report = report(now);
        var catalog = catalogService.get(catalogService.defaultConstructionCatalogCode());
        var tradeRef = catalog.path("selectedSupervisionWorkModeCatalog").path("tradeRefs").get(0);
        var subTradeRef = tradeRef.path("subTradeRefs").get(0);
        var processRef = subTradeRef.path("workCategories").get(0).path("processGroupRefs").get(0);
        var itemCode = processRef.path("itemRefs").get(0).asText();
        var tradeCode = tradeRef.path("tradeCode").asText();
        var subTradeCode = subTradeRef.path("subTradeCode").asText("NONE");
        var atoms = catalog.path("canonicalAtoms");
        var dailyPayload = Map.<String, Object>of(
                "dailyItems", Map.of(
                        "groups", List.of(Map.of(
                                "groupType", "TRADE",
                                "tradeCode", tradeCode,
                                "tradeName", text(atoms.path("trades").path(tradeCode), "name"),
                                "subTradeCode", subTradeCode,
                                "subTradeName", subTradeRef.path("subTradeName").asText(""),
                                "processCode", processRef.path("code").asText(),
                                "floor", "3층",
                                "entries", List.of(Map.of(
                                        "inspectionItemCode", itemCode,
                                        "inspectionItemName", text(atoms.path("inspectionItems").path(itemCode), "name"),
                                        "checklistRows", List.of(Map.of(
                                                "code", itemCode,
                                                "label", text(atoms.path("inspectionItems").path(itemCode), "name"),
                                                "result", "COMPLIANT",
                                                "referenceNote", "기준 확인",
                                                "actionNote", "조치 없음"))))))));
        arrange(report, dailyPayload, now);

        var response = service.preview(100L, "TRADE", new UserPrincipal(7L, "writer@test.co.kr"));

        assertThat(response.documentCount()).isEqualTo(1);
        assertThat(response.checkedRowCount()).isEqualTo(1);
        assertThat(response.documents()).singleElement().satisfies(document -> {
            assertThat(document.checklistType()).isEqualTo("TRADE");
            assertThat(document.tradeCode()).isEqualTo(tradeCode);
            assertThat(document.totalRowCount()).isGreaterThan(document.checkedRowCount());
            assertThat(document.rows()).anySatisfy(row -> {
                assertThat(row.rowCode()).isEqualTo(itemCode);
                assertThat(row.result()).isEqualTo("COMPLIANT");
                assertThat(row.referenceNote()).isEqualTo("기준 확인");
            });
        });
        assertThat(response.html()).contains("공종별 감리 체크리스트").contains("○");
        assertThat(response.html()).contains("rowspan=\"");
    }

    @Test
    void phasePreviewExpandsWholePhaseChecklistWhenOneRowIsChecked() {
        OfficeContext.set(10L);
        var now = OffsetDateTime.parse("2026-06-19T10:00:00+09:00");
        var report = report(now);
        var catalog = catalogService.get(catalogService.defaultConstructionCatalogCode());
        var phaseRef = catalog.path("selectedSupervisionWorkModeCatalog").path("phaseRefs").get(0);
        var processRef = phaseRef.path("workCategories").get(0).path("processGroupRefs").get(0);
        var itemCode = processRef.path("itemRefs").get(0).asText();
        var phaseCode = phaseRef.path("phaseCode").asText();
        var atoms = catalog.path("canonicalAtoms");
        var dailyPayload = Map.<String, Object>of(
                "dailyItems", Map.of(
                        "groups", List.of(Map.of(
                                "groupType", "PHASE",
                                "phaseCode", phaseCode,
                                "phaseName", text(atoms.path("constructionPhases").path(phaseCode), "name"),
                                "processCode", processRef.path("code").asText(),
                                "entries", List.of(Map.of(
                                        "inspectionItemCode", itemCode,
                                        "inspectionItemName", text(atoms.path("inspectionItems").path(itemCode), "name"),
                                        "checklistRows", List.of(Map.of(
                                                "code", itemCode,
                                                "label", text(atoms.path("inspectionItems").path(itemCode), "name"),
                                                "result", "NON_COMPLIANT",
                                                "actionNote", "재확인 필요"))))))));
        arrange(report, dailyPayload, now);

        var response = service.preview(100L, "PHASE", new UserPrincipal(7L, "writer@test.co.kr"));

        assertThat(response.documentCount()).isEqualTo(1);
        assertThat(response.checkedRowCount()).isEqualTo(1);
        assertThat(response.documents()).singleElement().satisfies(document -> {
            assertThat(document.checklistType()).isEqualTo("PHASE");
            assertThat(document.constructionPhaseCode()).isEqualTo(phaseCode);
            assertThat(document.totalRowCount()).isGreaterThan(document.checkedRowCount());
            assertThat(document.rows()).anySatisfy(row -> {
                assertThat(row.rowCode()).isEqualTo(itemCode);
                assertThat(row.result()).isEqualTo("NON_COMPLIANT");
                assertThat(row.actionNote()).isEqualTo("재확인 필요");
            });
        });
        assertThat(response.html()).contains("단계별 감리업무 Check List").contains("○");
    }

    @Test
    void tradePreviewPrefixesChildChecklistRowsWithDash() {
        OfficeContext.set(10L);
        var now = OffsetDateTime.parse("2026-06-19T10:00:00+09:00");
        var report = report(now);
        var itemCode = "REINFORCED_CONCRETE_IT_794FCB67F5";
        var itemName = "콘크리트 압축강도 시험의 확인";
        var dailyPayload = Map.<String, Object>of(
                "dailyItems", Map.of(
                        "groups", List.of(Map.of(
                                "groupType", "TRADE",
                                "tradeCode", "REINFORCED_CONCRETE",
                                "tradeName", "철근 콘크리트 공사",
                                "subTradeCode", "NONE",
                                "subTradeName", "없음",
                                "processCode", "CONCRETE_STRENGTH_TEST",
                                "floor", "3층",
                                "entries", List.of(Map.of(
                                        "inspectionItemCode", itemCode,
                                        "inspectionItemName", itemName,
                                        "checklistRows", List.of(Map.of(
                                                "code", itemCode,
                                                "label", itemName,
                                                "result", "COMPLIANT"))))))));
        arrange(report, dailyPayload, now);

        var response = service.preview(100L, "TRADE", new UserPrincipal(7L, "writer@test.co.kr"));

        assertThat(response.documents()).singleElement().satisfies(document -> {
            assertThat(document.rows()).anySatisfy(row ->
                    assertThat(row.rowLabel()).isEqualTo("콘크리트 압축강도 시험의 확인"));
            assertThat(document.rows()).anySatisfy(row ->
                    assertThat(row.rowLabel()).isEqualTo("- 시험채취의 시기 확인"));
            assertThat(document.rows()).anySatisfy(row ->
                    assertThat(row.rowLabel()).isEqualTo("- 시험채취량 확인"));
        });
        assertThat(response.html()).contains("- 시험채취의 시기 확인");
    }

    @Test
    void docxExportCreatesWordPackageFromChecklistRows() throws IOException {
        OfficeContext.set(10L);
        var now = OffsetDateTime.parse("2026-06-19T10:00:00+09:00");
        var report = report(now);
        var catalog = catalogService.get(catalogService.defaultConstructionCatalogCode());
        var tradeRef = catalog.path("selectedSupervisionWorkModeCatalog").path("tradeRefs").get(0);
        var subTradeRef = tradeRef.path("subTradeRefs").get(0);
        var processRef = subTradeRef.path("workCategories").get(0).path("processGroupRefs").get(0);
        var itemCode = processRef.path("itemRefs").get(0).asText();
        var tradeCode = tradeRef.path("tradeCode").asText();
        var subTradeCode = subTradeRef.path("subTradeCode").asText("NONE");
        var atoms = catalog.path("canonicalAtoms");
        var itemName = text(atoms.path("inspectionItems").path(itemCode), "name");
        var dailyPayload = Map.<String, Object>of(
                "dailyItems", Map.of(
                        "groups", List.of(Map.of(
                                "groupType", "TRADE",
                                "tradeCode", tradeCode,
                                "tradeName", text(atoms.path("trades").path(tradeCode), "name"),
                                "subTradeCode", subTradeCode,
                                "subTradeName", subTradeRef.path("subTradeName").asText(""),
                                "processCode", processRef.path("code").asText(),
                                "floor", "3층",
                                "entries", List.of(Map.of(
                                        "inspectionItemCode", itemCode,
                                        "inspectionItemName", itemName,
                                        "checklistRows", List.of(Map.of(
                                                "code", itemCode,
                                                "label", itemName,
                                                "result", "COMPLIANT",
                                                "referenceNote", "기준 확인",
                                                "actionNote", "조치 없음"))))))));
        arrange(report, dailyPayload, now);

        var export = new ChecklistDocxExportService(service)
                .export(100L, "TRADE", new UserPrincipal(7L, "writer@test.co.kr"));

        assertThat(export.fileName()).endsWith(".docx");
        assertThat(export.content()[0]).isEqualTo((byte) 'P');
        assertThat(export.content()[1]).isEqualTo((byte) 'K');
        var documentXml = zipEntry(export.content(), "word/document.xml");
        assertThat(documentXml).contains(escapeXml(itemName));
        assertThat(documentXml).contains("○");
        assertThat(documentXml).contains("<w:vMerge w:val=\"restart\"/>");
        assertThat(documentXml).contains("<w:vMerge/>");
    }

    @Test
    void checklistReportPreviewUsesSavedSourceSelectionAcrossDailyReports() {
        var now = OffsetDateTime.parse("2026-06-19T10:00:00+09:00");
        var checklistReport = reportOf(
                200L,
                "CONSTRUCTION_SUPERVISION_CHECKLIST",
                "CHK-001",
                "Checklist report",
                now);
        var includedDailyReport = reportOf(
                101L,
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "R-101",
                "Daily included",
                now);
        var excludedDailyReport = reportOf(
                102L,
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "R-102",
                "Daily excluded",
                now);
        var catalog = catalogService.get(catalogService.defaultConstructionCatalogCode());
        var tradeRef = catalog.path("selectedSupervisionWorkModeCatalog").path("tradeRefs").get(0);
        var subTradeRef = tradeRef.path("subTradeRefs").get(0);
        var processRef = subTradeRef.path("workCategories").get(0).path("processGroupRefs").get(0);
        var itemCode = processRef.path("itemRefs").get(0).asText();
        var tradeCode = tradeRef.path("tradeCode").asText();
        var subTradeCode = subTradeRef.path("subTradeCode").asText("NONE");
        var atoms = catalog.path("canonicalAtoms");
        var dailyPayload = Map.<String, Object>of(
                "dailyItems", Map.of(
                        "groups", List.of(Map.of(
                                "groupType", "TRADE",
                                "tradeCode", tradeCode,
                                "tradeName", text(atoms.path("trades").path(tradeCode), "name"),
                                "subTradeCode", subTradeCode,
                                "subTradeName", subTradeRef.path("subTradeName").asText(""),
                                "processCode", processRef.path("code").asText(),
                                "floor", "1F",
                                "entries", List.of(Map.of(
                                        "inspectionItemCode", itemCode,
                                        "inspectionItemName", text(atoms.path("inspectionItems").path(itemCode), "name"),
                                        "checklistRows", List.of(Map.of(
                                                "code", itemCode,
                                                "label", text(atoms.path("inspectionItems").path(itemCode), "name"),
                                                "result", "COMPLIANT",
                                                "referenceNote", "included report",
                                                "actionNote", ""))))))));
        var selectionStep = new InspectionReportStep(
                checklistReport,
                "CHECKLIST_SOURCE",
                PayloadStorageMode.CLOUD_ENCRYPTED,
                Map.of("checklistSelection", Map.of(
                        "outputType", "TRADE",
                        "selectionMode", "DATE_RANGE",
                        "dateFrom", "2026-06-10",
                        "dateTo", "2026-06-10")),
                7L,
                now);
        when(reportRepository.findByIdAndOfficeId(200L, 10L)).thenReturn(Optional.of(checklistReport));
        when(reportRepository.findByOfficeIdAndProjectIdOrderByUpdatedAtDesc(10L, 20L))
                .thenReturn(List.of(includedDailyReport, excludedDailyReport));
        when(stepRepository.findByReportIdAndStepCode(200L, "CHECKLIST_SOURCE")).thenReturn(Optional.of(selectionStep));
        when(stepRepository.findByReportIdAndStepCode(101L, "BASIC_INFO")).thenReturn(Optional.of(step(
                includedDailyReport,
                "BASIC_INFO",
                Map.of("inspectionDate", "2026-06-10"),
                now)));
        when(stepRepository.findByReportIdAndStepCode(102L, "BASIC_INFO")).thenReturn(Optional.of(step(
                excludedDailyReport,
                "BASIC_INFO",
                Map.of("inspectionDate", "2026-06-11"),
                now)));
        when(stepRepository.findByReportIdAndStepCode(101L, "DAILY_LOG")).thenReturn(Optional.of(step(
                includedDailyReport,
                "DAILY_LOG",
                dailyPayload,
                now)));
        when(stepRepository.findByReportIdAndStepCode(102L, "DAILY_LOG")).thenReturn(Optional.of(step(
                excludedDailyReport,
                "DAILY_LOG",
                dailyPayload,
                now)));

        var response = service.previewSystem(10L, 200L, null);

        assertThat(response.checklistType()).isEqualTo("TRADE");
        assertThat(response.documentCount()).isEqualTo(1);
        assertThat(response.checkedRowCount()).isEqualTo(1);
        assertThat(response.documents()).singleElement().satisfies(document ->
                assertThat(document.rows()).anySatisfy(row ->
                        assertThat(row.referenceNote()).isEqualTo("included report")));
    }

    @Test
    void systemPreviewResolvesSiteModeWithExplicitOfficeIdWithoutThreadLocalOfficeContext() {
        OfficeContext.clear();
        var now = OffsetDateTime.parse("2026-06-19T10:00:00+09:00");
        var siteRepository = mock(SiteRepository.class);
        var catalogService = new SupervisionDomainCatalogService(objectMapper, siteRepository);
        var localService = new ChecklistPrintReadService(
                reportRepository,
                stepRepository,
                permissionService,
                catalogService,
                objectMapper);
        var report = report(now);
        when(reportRepository.findByIdAndOfficeId(100L, 10L)).thenReturn(Optional.of(report));
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG")).thenReturn(Optional.empty());
        when(siteRepository.findByIdAndOfficeId(30L, 10L)).thenReturn(Optional.empty());

        var response = localService.previewSystem(10L, 100L, "TRADE");

        assertThat(response.reportId()).isEqualTo(100L);
        verify(siteRepository).findByIdAndOfficeId(30L, 10L);
    }

    private void arrange(InspectionReport report, Map<String, Object> dailyPayload, OffsetDateTime now) {
        var step = new InspectionReportStep(
                report,
                "DAILY_LOG",
                PayloadStorageMode.CLOUD_ENCRYPTED,
                dailyPayload,
                7L,
                now);
        when(reportRepository.findByIdAndOfficeId(100L, 10L)).thenReturn(Optional.of(report));
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG")).thenReturn(Optional.of(step));
        when(permissionService.canWriteReport(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(true);
    }

    private InspectionReport reportOf(Long id, String reportType, String reportNo, String title, OffsetDateTime now) {
        var report = new InspectionReport(
                10L,
                20L,
                30L,
                reportNo,
                reportType,
                title,
                40L,
                7L,
                now);
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }

    private InspectionReportStep step(
            InspectionReport report,
            String stepCode,
            Map<String, Object> payload,
            OffsetDateTime now
    ) {
        return new InspectionReportStep(
                report,
                stepCode,
                PayloadStorageMode.CLOUD_ENCRYPTED,
                payload,
                7L,
                now);
    }

    private InspectionReport report(OffsetDateTime now) {
        var report = new InspectionReport(
                10L,
                20L,
                30L,
                "R-001",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "체크리스트 출력 테스트",
                40L,
                7L,
                now);
        ReflectionTestUtils.setField(report, "id", 100L);
        return report;
    }

    private String text(JsonNode node, String key) {
        return node.path(key).asText("");
    }

    private String zipEntry(byte[] content, String entryName) throws IOException {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                if (entryName.equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
                entry = zip.getNextEntry();
            }
        }
        throw new AssertionError(entryName + " not found");
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

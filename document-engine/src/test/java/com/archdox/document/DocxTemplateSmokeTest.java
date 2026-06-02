package com.archdox.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class DocxTemplateSmokeTest {
    private static final String KO_CONSTRUCTION_SUPERVISION_REPORT = "\uAC10\uB9AC\uBCF4\uACE0\uC11C";
    private static final String KO_CONSTRUCTION_DAILY_LOG = "\uACF5\uC0AC\uAC10\uB9AC\uC77C\uC9C0";
    private static final String KO_DEMOLITION_SAFETY_CHECKLIST =
            "\uD574\uCCB4\uACF5\uC0AC \uC548\uC804\uC810\uAC80\uD45C";
    private static final String KO_SERIAL_NO = "\uC77C\uB828\uBC88\uD638";
    private static final String KO_WEATHER = "\uB0A0\uC528";
    private static final String KO_CHIEF_SUPERVISOR = "\uCD1D\uAD04\uAC10\uB9AC\uCC45\uC784\uC790";
    private static final String KO_ARCHITECT_ASSISTANT = "\uAC74\uCD95\uC0AC\uBCF4";
    private static final String KO_CONSTRUCTION_NAME = "\uACF5\uC0AC\uBA85";
    private static final String KO_INSPECTION_DATE = "\uAC10\uB9AC\uC77C\uC790";
    private static final String KO_TRADE_AND_PROCESS =
            "\uACF5\uC885 \uBC0F \uC138\uBD80\uACF5\uC815";
    private static final String KO_SUPERVISION_ITEM = "\uAC10\uB9AC \uD56D\uBAA9";
    private static final String KO_SUPERVISION_CONTENT = "\uAC10\uB9AC\uB0B4\uC6A9";
    private static final String KO_SPECIAL_NOTES = "\uD2B9\uAE30\uC0AC\uD56D";
    private static final String KO_ISSUE_AND_ACTION =
            "\uC9C0\uC801\uC0AC\uD56D \uBC0F \uCC98\uB9AC\uACB0\uACFC";
    private static final String KO_PERMIT_NUMBER = "\uD5C8\uAC00\uBC88\uD638";
    private static final String KO_PERMIT_DATE = "\uD5C8\uAC00\uC77C\uC790";
    private static final String KO_SITE_ADDRESS = "\uB300\uC9C0\uC704\uCE58";
    private static final String KO_LOT_NUMBER = "\uC9C0\uBC88";
    private static final String KO_SUPERVISION_PERIOD = "\uACF5\uC0AC\uAC10\uB9AC\uAE30\uAC04";
    private static final String KO_SUPERVISOR = "\uAC10\uB9AC\uC790";
    private static final String KO_COMPREHENSIVE_OPINION = "\uC885\uD569\uC758\uACAC";
    private static final String KO_CHECK_ITEM = "\uAC80\uC0AC\uD56D\uBAA9";
    private static final String KO_CHECK_CRITERIA = "\uAC80\uC0AC\uAE30\uC900";
    private static final String KO_CHECK_RESULT = "\uAC80\uC0AC\uACB0\uACFC";
    private static final String KO_ACTION = "\uC870\uCE58\uC0AC\uD56D";
    private static final String KO_CHECK_LOCATION = "\uC810\uAC80\uC704\uCE58";
    private static final String KO_DEMOLITION_WORKER = "\uD574\uCCB4\uC791\uC5C5\uC790";
    private static final String KO_WORK_STAGE = "\uC791\uC5C5\uB2E8\uACC4";

    @Test
    void rendersRealisticInspectionTemplateWithPhotosAndChecklistTables() throws Exception {
        var template = realisticInspectionTemplate();
        var image = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lz7S4QAAAABJRU5ErkJggg==");
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                photo -> Optional.of(new ResolvedPhotoContent(image, "image/png")),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-smoke-1",
                "office-smoke",
                "report-smoke-1",
                new TemplateSpec("DAILY_SUPERVISION", 2, "templates/smoke-inspection.docx", "{}", "{}"),
                Map.of(
                        "templateFields", Map.of(
                                "projectName", "Project Alpha",
                                "siteName", "Tower A",
                                "inspectionDate", "2026-05-23",
                                "inspectorName", "Inspector Kim",
                                "reportTitle", "Daily Safety Inspection"),
                        "layoutSections", Map.of(
                                "photoSection", Map.of(
                                        "type", "PHOTO_TABLE",
                                        "title", "Site Photos",
                                        "photosPerRow", 2,
                                        "imageSize", "THUMBNAIL",
                                        "tableStyle", "ArchDoxInspectionTable",
                                        "headerFill", "FFF2CC",
                                        "borderColor", "C9A227",
                                        "fields", List.of(
                                                Map.of("label", "Caption", "source", "caption"),
                                                Map.of("label", "Step", "source", "stepCode"))),
                                "checklistSection", Map.of(
                                        "type", "CHECKLIST_TABLE",
                                        "title", "Checklist Results",
                                        "tableStyle", "ArchDoxInspectionTable",
                                        "headerFill", "FFF2CC",
                                        "borderColor", "C9A227",
                                        "fields", List.of(
                                                Map.of("label", "Code", "source", "itemCode", "width", 1800),
                                                Map.of("label", "Item", "source", "label", "width", 4200),
                                                Map.of("label", "Result", "source", "answer.value", "width", 1400),
                                                Map.of("label", "Note", "source", "note", "width", 1600)))),
                        "checklistAnswers", List.of(
                                Map.of(
                                        "itemCode", "SAFE-001",
                                        "label", "Opening guard",
                                        "answer", Map.of("value", "OK"),
                                        "note", "Installed"),
                                Map.of(
                                        "itemCode", "SAFE-002",
                                        "label", "Material stacking",
                                        "answer", Map.of("value", "NEEDS_ACTION"),
                                        "note", "Move from passage"))),
                List.of(
                        new PhotoAsset("photo-1", "PHOTO_STEP", "photos/smoke/front.png", "Front elevation", PhotoLayoutSize.MEDIUM, "image/png", null),
                        new PhotoAsset("photo-2", "PHOTO_STEP", "photos/smoke/detail.png", "Opening guard detail", PhotoLayoutSize.MEDIUM, "image/png", null)),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        assertEquals(ArtifactType.DOCX, result.artifacts().get(0).type());

        var content = result.artifacts().get(0).content();
        var documentXml = zipEntry(content, "word/document.xml");
        assertTrue(documentXml.contains("ArchDox Inspection Report"));
        assertTrue(documentXml.contains("Project Alpha"));
        assertTrue(documentXml.contains("Tower A"));
        assertTrue(documentXml.contains("2026-05-23"));
        assertTrue(documentXml.contains("Inspector Kim"));
        assertTrue(documentXml.contains("Daily Safety Inspection"));
        assertTrue(documentXml.contains("Site Photos"));
        assertTrue(documentXml.contains("Front elevation"));
        assertTrue(documentXml.contains("Opening guard detail"));
        assertTrue(documentXml.contains("Checklist Results"));
        assertTrue(documentXml.contains("SAFE-001"));
        assertTrue(documentXml.contains("Opening guard"));
        assertTrue(documentXml.contains("NEEDS_ACTION"));
        assertTrue(documentXml.contains("Move from passage"));
        assertTrue(documentXml.contains("<w:tblStyle w:val=\"ArchDoxInspectionTable\"/>"));
        assertTrue(documentXml.contains("w:color=\"C9A227\""));
        assertTrue(documentXml.contains("<w:shd w:fill=\"FFF2CC\"/>"));
        assertTrue(countOccurrences(documentXml, "<w:tbl>") >= 3);
        assertTrue(!documentXml.contains("${"));

        assertTrue(zipEntry(content, "word/_rels/document.xml.rels").contains("rIdArchDoxImage1"));
        assertTrue(zipEntry(content, "[Content_Types].xml").contains("Extension=\"png\""));
        assertNotNull(zipEntryBytes(content, "word/media/archdox-photo-1.png"));
        assertNotNull(zipEntryBytes(content, "word/media/archdox-photo-2.png"));

        writeSmokeArtifact(content);
    }

    @Test
    void rendersHwpDerivedConstructionSupervisionDailyLogMapping() throws Exception {
        var template = constructionSupervisionDailyLogTemplate();
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-hwp-derived-1",
                "office-smoke",
                "report-hwp-derived-1",
                new TemplateSpec("CONSTRUCTION_SUPERVISION_DAILY_LOG", 1, "templates/hwp-derived-daily-log.docx", "{}", "{}"),
                Map.of(
                        "templateFields", Map.of(
                                "serialNo", "DL-2026-0523-001",
                                "projectName", "Project Alpha 신축공사",
                                "inspectionDate", "2026-05-23",
                                "dayOfWeek", "토",
                                "weather", "맑음",
                                "chiefSupervisorName", "김감리",
                                "assistantArchitectName", "박건축",
                                "specialNotes", "3층 슬래브 타설 전 철근 배근 상태를 확인함.",
                                "correctionResults", "개구부 주변 안전난간 보강 지시 및 조치 완료 확인."),
                        "layoutSections", Map.of(
                                "supervisionItemsSection", Map.of(
                                        "type", "CHECKLIST_TABLE",
                                        "includeTitle", false,
                                        "tableStyle", "ArchDoxInspectionTable",
                                        "headerFill", "FFF2CC",
                                        "borderColor", "C9A227",
                                        "fields", List.of(
                                                Map.of("label", "공종 및 세부공정 (층)", "source", "answer.trade", "width", 3000),
                                                Map.of("label", "감리 항목", "source", "label", "width", 2500),
                                                Map.of("label", "감리내용", "source", "note", "width", 3500)))),
                        "checklistAnswers", List.of(
                                Map.of(
                                        "itemCode", "LOG-001",
                                        "label", "슬래브 철근 배근 상태",
                                        "answer", Map.of("trade", "철근콘크리트공사 (3층)", "value", "확인"),
                                        "note", "배근 간격, 정착 길이 및 피복두께 확인"),
                                Map.of(
                                        "itemCode", "LOG-002",
                                        "label", "안전난간 설치 상태",
                                        "answer", Map.of("trade", "가설공사 (3층)", "value", "보완"),
                                        "note", "개구부 주변 안전난간 추가 보강 지시"))),
                List.of(),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        var content = result.artifacts().get(0).content();
        var documentXml = zipEntry(content, "word/document.xml");
        assertTrue(documentXml.contains("공사감리일지"));
        assertTrue(documentXml.contains("DL-2026-0523-001"));
        assertTrue(documentXml.contains("김감리"));
        assertTrue(documentXml.contains("박건축"));
        assertTrue(documentXml.contains("Project Alpha 신축공사"));
        assertTrue(documentXml.contains("2026-05-23"));
        assertTrue(documentXml.contains("토"));
        assertTrue(documentXml.contains("맑음"));
        assertTrue(documentXml.contains("공종 및 세부공정 (층)"));
        assertTrue(documentXml.contains("철근콘크리트공사 (3층)"));
        assertTrue(documentXml.contains("슬래브 철근 배근 상태"));
        assertTrue(documentXml.contains("배근 간격, 정착 길이 및 피복두께 확인"));
        assertTrue(documentXml.contains("특기사항"));
        assertTrue(documentXml.contains("지적사항 및 처리결과"));
        assertTrue(documentXml.contains("공종에는 주요공종 및 단위공종 그리고 해당 층수를 기재합니다."));
        assertTrue(documentXml.contains("<w:tblStyle w:val=\"ArchDoxInspectionTable\"/>"));
        assertTrue(!documentXml.contains("${"));

        writeSmokeArtifact("hwp-derived-construction-supervision-daily-log.docx", content);
    }

    @Test
    void rendersPdfReferenceConstructionDailySupervisionLogTemplate() throws Exception {
        var template = pdfReferenceConstructionDailyLogTemplate();
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-pdf-reference-daily-log-1",
                "office-smoke",
                "report-pdf-reference-daily-log-1",
                new TemplateSpec(
                        "KOREAN_CONSTRUCTION_DAILY_SUPERVISION_APPENDIX_2",
                        1,
                        "templates/korean/korean-construction-daily-supervision-log-appendix-2.docx",
                        "{}",
                        "{}"),
                Map.of(
                        "templateFields", Map.of(
                                "serialNo", "DL-2026-0524-001",
                                "chiefSupervisorName", "Supervisor Kim",
                                "architectAssistantName", "Assistant Park",
                                "constructionName", "Reference Tower",
                                "inspectionDate", "2026-05-24",
                                "inspectionDayOfWeek", "Sunday",
                                "weather", "Clear",
                                "specialNotes", "Slab rebar spacing and cover depth checked.",
                                "issueAndAction", "Opening guardrail reinforcement completed."),
                        "layoutSections", Map.of(
                                "supervisionItemsSection", Map.of(
                                        "type", "CHECKLIST_TABLE",
                                        "includeTitle", false,
                                        "tableStyle", "ArchDoxInspectionTable",
                                        "headerFill", "FFF2CC",
                                        "borderColor", "C9A227",
                                        "fields", List.of(
                                                Map.of("label", KO_TRADE_AND_PROCESS, "source", "answer.trade", "width", 3000),
                                                Map.of("label", KO_SUPERVISION_ITEM, "source", "label", "width", 2500),
                                                Map.of("label", KO_SUPERVISION_CONTENT, "source", "note", "width", 3500)))),
                        "checklistAnswers", List.of(
                                Map.of(
                                        "itemCode", "LOG-001",
                                        "label", "Slab rebar placement",
                                        "answer", Map.of("trade", "Reinforced concrete / 3F", "value", "OK"),
                                        "note", "Spacing, anchorage length, and cover depth verified."),
                                Map.of(
                                        "itemCode", "LOG-002",
                                        "label", "Temporary safety rail",
                                        "answer", Map.of("trade", "Temporary work / 3F", "value", "ACTION_DONE"),
                                        "note", "Opening guardrail added and photo evidence attached."))),
                List.of(),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        var content = result.artifacts().get(0).content();
        var documentXml = zipEntry(content, "word/document.xml");
        assertTrue(documentXml.contains("[별지 제2호서식]"));
        assertTrue(documentXml.contains(KO_CONSTRUCTION_DAILY_LOG));
        assertTrue(documentXml.contains("일련번호"));
        assertTrue(documentXml.contains("총괄감리책임자"));
        assertTrue(documentXml.contains("건축사보"));
        assertTrue(documentXml.contains("공사명"));
        assertTrue(documentXml.contains("공종 및 세부공정"));
        assertTrue(documentXml.contains("감리 항목"));
        assertTrue(documentXml.contains("감리내용"));
        assertTrue(documentXml.contains("Supervisor Kim"));
        assertTrue(documentXml.contains("Reference Tower"));
        assertTrue(documentXml.contains("2026"));
        assertTrue(documentXml.contains("24"));
        assertTrue(documentXml.contains("Reinforced concrete / 3F"));
        assertTrue(documentXml.contains("Slab rebar placement"));
        assertTrue(documentXml.contains("Opening guardrail added"));
        assertTrue(documentXml.contains(KO_SPECIAL_NOTES));
        assertTrue(documentXml.contains(KO_ISSUE_AND_ACTION));
        assertTrue(documentXml.contains("작성방법"));
        assertTrue(!documentXml.contains("<w:tblStyle w:val=\"ArchDoxInspectionTable\"/>"));
        assertTrue(!documentXml.contains("C9A227"));
        assertTrue(!documentXml.contains("${"));
        assertTrue(countOccurrences(documentXml, "<w:tbl>") >= 5);

        writeSmokeArtifact("pdf-reference-construction-daily-supervision-log.docx", content);
    }

    @Test
    void rendersPdfReferenceConstructionSupervisionReportTemplate() throws Exception {
        var template = pdfReferenceConstructionSupervisionReportTemplate();
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-pdf-reference-construction-report-1",
                "office-smoke",
                "report-pdf-reference-construction-report-1",
                new TemplateSpec(
                        "KOREAN_CONSTRUCTION_SUPERVISION_REPORT_APPENDIX_1",
                        1,
                        "templates/pdf-reference-construction-supervision-report.docx",
                        "{}",
                        "{}"),
                Map.of(
                        "templateFields", Map.ofEntries(
                                Map.entry("permitNumber", "ARCH-2026-001"),
                                Map.entry("permitDate", "2026-05-01"),
                                Map.entry("siteAddress", "Seoul Gangnam-gu Reference-ro 10"),
                                Map.entry("lotNumber", "123-4"),
                                Map.entry("constructionName", "Reference Tower"),
                                Map.entry("supervisionStartDate", "2026-05-01"),
                                Map.entry("supervisionEndDate", "2026-08-31"),
                                Map.entry("chiefSupervisorName", "Supervisor Kim"),
                                Map.entry("supervisorName", "Inspector Park"),
                                Map.entry("specialNotes", "Structure, waterproofing, and evacuation path items reviewed.")),
                        "layoutSections", Map.of(
                                "reportOpinionSection", Map.of(
                                        "type", "CHECKLIST_TABLE",
                                        "title", KO_COMPREHENSIVE_OPINION,
                                        "tableStyle", "ArchDoxInspectionTable",
                                        "headerFill", "FFF2CC",
                                        "borderColor", "C9A227",
                                        "fields", List.of(
                                                Map.of("label", "Code", "source", "itemCode", "width", 1800),
                                                Map.of("label", KO_SUPERVISION_ITEM, "source", "label", "width", 2800),
                                                Map.of("label", KO_CHECK_RESULT, "source", "answer.value", "width", 1800),
                                                Map.of("label", KO_SUPERVISION_CONTENT, "source", "note", "width", 2600)))),
                        "checklistAnswers", List.of(
                                Map.of(
                                        "itemCode", "RPT-001",
                                        "label", "Load path review",
                                        "answer", Map.of("value", "Conforming"),
                                        "note", "Column, beam, and slab load path reviewed."),
                                Map.of(
                                        "itemCode", "RPT-002",
                                        "label", "Fire safety separation",
                                        "answer", Map.of("value", "Needs follow-up"),
                                        "note", "Confirm sealant material certificate before completion."))),
                List.of(),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        var content = result.artifacts().get(0).content();
        var documentXml = zipEntry(content, "word/document.xml");
        assertTrue(documentXml.contains(KO_CONSTRUCTION_SUPERVISION_REPORT));
        assertTrue(documentXml.contains(KO_PERMIT_NUMBER));
        assertTrue(documentXml.contains("ARCH-2026-001"));
        assertTrue(documentXml.contains(KO_SITE_ADDRESS));
        assertTrue(documentXml.contains("Seoul Gangnam-gu Reference-ro 10"));
        assertTrue(documentXml.contains(KO_SUPERVISION_PERIOD));
        assertTrue(documentXml.contains("2026-05-01 ~ 2026-08-31"));
        assertTrue(documentXml.contains(KO_COMPREHENSIVE_OPINION));
        assertTrue(documentXml.contains("Load path review"));
        assertTrue(documentXml.contains("Needs follow-up"));
        assertTrue(documentXml.contains("Confirm sealant material certificate"));
        assertTrue(!documentXml.contains("${"));
        assertTrue(countOccurrences(documentXml, "<w:tbl>") >= 2);

        writeSmokeArtifact("pdf-reference-construction-supervision-report.docx", content);
    }

    @Test
    void rendersPdfReferenceDemolitionSafetyChecklistTemplate() throws Exception {
        var template = pdfReferenceDemolitionSafetyChecklistTemplate();
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-pdf-reference-demolition-safety-1",
                "office-smoke",
                "report-pdf-reference-demolition-safety-1",
                new TemplateSpec(
                        "KOREAN_DEMOLITION_SAFETY_CHECK_APPENDIX_1",
                        1,
                        "templates/pdf-reference-demolition-safety-checklist.docx",
                        "{}",
                        "{}"),
                Map.of(
                        "templateFields", Map.of(
                                "safetyInspectionDate", "2026-05-24",
                                "inspectionLocation", "Reference Building B1",
                                "supervisorName", "Supervisor Lee",
                                "demolitionWorkerName", "Worker Choi",
                                "safetyCheckStage", "Interior wall demolition",
                                "correctiveAction", "Install additional temporary support before next stage."),
                        "layoutSections", Map.of(
                                "safetyChecklistSection", Map.of(
                                        "type", "CHECKLIST_TABLE",
                                        "title", KO_DEMOLITION_SAFETY_CHECKLIST,
                                        "tableStyle", "ArchDoxInspectionTable",
                                        "headerFill", "FFF2CC",
                                        "borderColor", "C9A227",
                                        "fields", List.of(
                                                Map.of("label", KO_CHECK_ITEM, "source", "label", "width", 2500),
                                                Map.of("label", KO_CHECK_CRITERIA, "source", "answer.criteria", "width", 2500),
                                                Map.of("label", KO_CHECK_RESULT, "source", "answer.result", "width", 1800),
                                                Map.of("label", KO_ACTION, "source", "note", "width", 2200)))),
                        "checklistAnswers", List.of(
                                Map.of(
                                        "itemCode", "DEM-001",
                                        "label", "Temporary support spacing",
                                        "answer", Map.of("criteria", "Support interval within plan", "result", "Pass"),
                                        "note", "Checked before demolition."),
                                Map.of(
                                        "itemCode", "DEM-002",
                                        "label", "Dust control",
                                        "answer", Map.of("criteria", "Water spray and cover installed", "result", "Action required"),
                                        "note", "Add cover at north entrance."))),
                List.of(),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        var content = result.artifacts().get(0).content();
        var documentXml = zipEntry(content, "word/document.xml");
        assertTrue(documentXml.contains(KO_DEMOLITION_SAFETY_CHECKLIST));
        assertTrue(documentXml.contains("2026-05-24"));
        assertTrue(documentXml.contains(KO_CHECK_LOCATION));
        assertTrue(documentXml.contains("Reference Building B1"));
        assertTrue(documentXml.contains(KO_DEMOLITION_WORKER));
        assertTrue(documentXml.contains("Interior wall demolition"));
        assertTrue(documentXml.contains(KO_CHECK_CRITERIA));
        assertTrue(documentXml.contains(KO_CHECK_RESULT));
        assertTrue(documentXml.contains("Temporary support spacing"));
        assertTrue(documentXml.contains("Action required"));
        assertTrue(documentXml.contains("Add cover at north entrance."));
        assertTrue(!documentXml.contains("${"));
        assertTrue(countOccurrences(documentXml, "<w:tbl>") >= 2);

        writeSmokeArtifact("pdf-reference-demolition-safety-checklist.docx", content);
    }

    private byte[] pdfReferenceConstructionDailyLogTemplate() throws Exception {
        return docxWithBodyXml(String.join(
                "\n",
                paragraph(KO_CONSTRUCTION_DAILY_LOG),
                simpleTable(
                        simpleRow(KO_SERIAL_NO, "${serialNo}", KO_WEATHER, "${weather}")
                                + simpleRow(KO_CHIEF_SUPERVISOR, "${chiefSupervisorName}", KO_ARCHITECT_ASSISTANT, "${architectAssistantName}")
                                + simpleRow(KO_CONSTRUCTION_NAME, "${constructionName}", KO_INSPECTION_DATE, "${inspectionDate} ${inspectionDayOfWeek}")),
                paragraph("${supervisionItemsSection}"),
                paragraph(KO_SPECIAL_NOTES),
                paragraph("${specialNotes}"),
                paragraph(KO_ISSUE_AND_ACTION),
                paragraph("${issueAndAction}")));
    }

    private byte[] pdfReferenceConstructionSupervisionReportTemplate() throws Exception {
        return docxWithBodyXml(String.join(
                "\n",
                paragraph(KO_CONSTRUCTION_SUPERVISION_REPORT),
                simpleTable(
                        simpleRow(KO_PERMIT_NUMBER, "${permitNumber}", KO_PERMIT_DATE, "${permitDate}")
                                + simpleRow(KO_SITE_ADDRESS, "${siteAddress}", KO_LOT_NUMBER, "${lotNumber}")
                                + simpleRow(KO_CONSTRUCTION_NAME, "${constructionName}", KO_SUPERVISOR, "${chiefSupervisorName} / ${supervisorName}")
                                + simpleRow(KO_SUPERVISION_PERIOD, "${supervisionStartDate} ~ ${supervisionEndDate}", KO_SPECIAL_NOTES, "${specialNotes}")),
                paragraph("${reportOpinionSection}")));
    }

    private byte[] pdfReferenceDemolitionSafetyChecklistTemplate() throws Exception {
        return docxWithBodyXml(String.join(
                "\n",
                paragraph(KO_DEMOLITION_SAFETY_CHECKLIST),
                simpleTable(
                        simpleRow(KO_INSPECTION_DATE, "${safetyInspectionDate}", KO_CHECK_LOCATION, "${inspectionLocation}")
                                + simpleRow(KO_SUPERVISOR, "${supervisorName}", KO_DEMOLITION_WORKER, "${demolitionWorkerName}")
                                + simpleRow(KO_WORK_STAGE, "${safetyCheckStage}", KO_ACTION, "${correctiveAction}")),
                paragraph("${safetyChecklistSection}")));
    }

    private byte[] realisticInspectionTemplate() throws Exception {
        return docxWithBodyXml("""
                <w:p><w:r><w:t>ArchDox Inspection Report</w:t></w:r></w:p>
                <w:p><w:r><w:t>${reportTitle}</w:t></w:r></w:p>
                <w:tbl>
                  <w:tblPr>
                    <w:tblW w:w="9000" w:type="dxa"/>
                    <w:tblBorders>
                      <w:top w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:left w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:bottom w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:right w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:insideH w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:insideV w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                    </w:tblBorders>
                  </w:tblPr>
                  <w:tblGrid><w:gridCol w:w="2500"/><w:gridCol w:w="6500"/></w:tblGrid>
                  <w:tr>
                    <w:tc><w:tcPr><w:tcW w:w="2500" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>Project</w:t></w:r></w:p></w:tc>
                    <w:tc><w:tcPr><w:tcW w:w="6500" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>${pro</w:t></w:r><w:r><w:t>jectName}</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:tcPr><w:tcW w:w="2500" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>Site</w:t></w:r></w:p></w:tc>
                    <w:tc><w:tcPr><w:tcW w:w="6500" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>${siteName}</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:tcPr><w:tcW w:w="2500" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>Date</w:t></w:r></w:p></w:tc>
                    <w:tc><w:tcPr><w:tcW w:w="6500" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>${inspectionDate}</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:tcPr><w:tcW w:w="2500" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>Inspector</w:t></w:r></w:p></w:tc>
                    <w:tc><w:tcPr><w:tcW w:w="6500" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>${inspectorName}</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
                <w:p><w:r><w:t>Photo Section</w:t></w:r></w:p>
                <w:p><w:r><w:t>${photoSection}</w:t></w:r></w:p>
                <w:p><w:r><w:t>Checklist Section</w:t></w:r></w:p>
                <w:p><w:r><w:t>${checklistSection}</w:t></w:r></w:p>
                """);
    }

    private byte[] constructionSupervisionDailyLogTemplate() throws Exception {
        return docxWithBodyXml("""
                <w:p><w:r><w:t>■ 건축공사 감리세부기준〔별지 제2호서식〕</w:t></w:r></w:p>
                <w:p><w:r><w:t>공사감리일지</w:t></w:r></w:p>
                <w:tbl>
                  <w:tblPr>
                    <w:tblW w:w="9000" w:type="dxa"/>
                    <w:tblBorders>
                      <w:top w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:left w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:bottom w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:right w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:insideH w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:insideV w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                    </w:tblBorders>
                  </w:tblPr>
                  <w:tblGrid><w:gridCol w:w="2200"/><w:gridCol w:w="2800"/><w:gridCol w:w="1800"/><w:gridCol w:w="2200"/></w:tblGrid>
                  <w:tr>
                    <w:tc><w:tcPr><w:tcW w:w="2200" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>일련번호</w:t></w:r></w:p></w:tc>
                    <w:tc><w:tcPr><w:tcW w:w="2800" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>${serialNo}</w:t></w:r></w:p></w:tc>
                    <w:tc><w:tcPr><w:tcW w:w="1800" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>날씨</w:t></w:r></w:p></w:tc>
                    <w:tc><w:tcPr><w:tcW w:w="2200" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>${weather}</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:tcPr><w:tcW w:w="2200" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>총괄감리책임자</w:t></w:r></w:p></w:tc>
                    <w:tc><w:tcPr><w:tcW w:w="2800" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>${chiefSupervisorName} (서명 또는 인)</w:t></w:r></w:p></w:tc>
                    <w:tc><w:tcPr><w:tcW w:w="1800" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>건축사보</w:t></w:r></w:p></w:tc>
                    <w:tc><w:tcPr><w:tcW w:w="2200" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>${assistantArchitectName} (서명 또는 인)</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:tcPr><w:tcW w:w="2200" w:type="dxa"/></w:tcPr><w:p><w:r><w:t>공사명</w:t></w:r></w:p></w:tc>
                    <w:tc><w:tcPr><w:tcW w:w="6800" w:type="dxa"/><w:gridSpan w:val="3"/></w:tcPr><w:p><w:r><w:t>${projectName} 공사 ${inspectionDate}(${dayOfWeek}요일)</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
                <w:p><w:r><w:t>${supervisionItemsSection}</w:t></w:r></w:p>
                <w:p><w:r><w:t>특기사항</w:t></w:r></w:p>
                <w:p><w:r><w:t>${specialNotes}</w:t></w:r></w:p>
                <w:p><w:r><w:t>지적사항 및 처리결과</w:t></w:r></w:p>
                <w:p><w:r><w:t>${correctionResults}</w:t></w:r></w:p>
                <w:p><w:r><w:t>작성방법</w:t></w:r></w:p>
                <w:p><w:r><w:t>1. 공종에는 주요공종 및 단위공종 그리고 해당 층수를 기재합니다.</w:t></w:r></w:p>
                <w:p><w:r><w:t>2. 감리항목은 공종별 감리 체크리스트를 기반으로 기재합니다.</w:t></w:r></w:p>
                <w:p><w:r><w:t>3. 감리내용에는 육안검사, 입회, 시험 등 감리내용과 결과를 구체적으로 기재합니다.</w:t></w:r></w:p>
                """);
    }

    private String paragraph(String value) {
        return "<w:p><w:r><w:t>" + value + "</w:t></w:r></w:p>";
    }

    private String simpleTable(String rowsXml) {
        return """
                <w:tbl>
                  <w:tblPr>
                    <w:tblW w:w="9000" w:type="dxa"/>
                    <w:tblBorders>
                      <w:top w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:left w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:bottom w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:right w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:insideH w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                      <w:insideV w:val="single" w:sz="4" w:space="0" w:color="D9DDE3"/>
                    </w:tblBorders>
                  </w:tblPr>
                  <w:tblGrid><w:gridCol w:w="2200"/><w:gridCol w:w="2800"/><w:gridCol w:w="1800"/><w:gridCol w:w="2200"/></w:tblGrid>
                  %s
                </w:tbl>
                """.formatted(rowsXml);
    }

    private String simpleRow(String firstLabel, String firstValue, String secondLabel, String secondValue) {
        return "<w:tr>"
                + simpleCell(firstLabel, "2200")
                + simpleCell(firstValue, "2800")
                + simpleCell(secondLabel, "1800")
                + simpleCell(secondValue, "2200")
                + "</w:tr>";
    }

    private String simpleCell(String value, String width) {
        return "<w:tc><w:tcPr><w:tcW w:w=\"" + width + "\" w:type=\"dxa\"/></w:tcPr>"
                + paragraph(value)
                + "</w:tc>";
    }

    private byte[] docxWithBodyXml(String bodyXml) throws Exception {
        try (var output = new ByteArrayOutputStream();
             var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            put(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        %s
                        <w:sectPr/>
                      </w:body>
                    </w:document>
                    """.formatted(bodyXml));
            zip.finish();
            return output.toByteArray();
        }
    }

    private void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String zipEntry(byte[] docx, String name) throws Exception {
        var bytes = zipEntryBytes(docx, name);
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] zipEntryBytes(byte[] docx, String name) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(docx), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (name.equals(entry.getName())) {
                    return zip.readAllBytes();
                }
            }
        }
        return null;
    }

    private int countOccurrences(String value, String needle) {
        var count = 0;
        var index = value.indexOf(needle);
        while (index >= 0) {
            count++;
            index = value.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private void writeSmokeArtifact(byte[] content) throws Exception {
        writeSmokeArtifact("realistic-inspection-smoke.docx", content);
    }

    private void writeSmokeArtifact(String fileName, byte[] content) throws Exception {
        var outputDirectory = Path.of("build", "archdox-smoke");
        Files.createDirectories(outputDirectory);
        Files.write(outputDirectory.resolve(fileName), content);
    }
}

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

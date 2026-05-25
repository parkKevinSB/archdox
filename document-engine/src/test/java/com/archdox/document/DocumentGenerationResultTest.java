package com.archdox.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class DocumentGenerationResultTest {
    @Test
    void createsFailedResultWithoutArtifacts() {
        var result = DocumentGenerationResult.failed("job-1", "TEMPLATE_ERROR", "Missing field");

        assertEquals("job-1", result.jobId());
        assertEquals(GenerationStatus.FAILED, result.status());
        assertTrue(result.artifacts().isEmpty());
    }

    @Test
    void simpleEngineGeneratesDocxArtifactContent() {
        var engine = new SimpleDocumentEngine();
        var result = engine.generate(new DocumentGenerationRequest(
                "job-1",
                "office-1",
                "report-1",
                new TemplateSpec("DAILY", 1, "templates/daily.docx", "{}", "{}"),
                Map.of("title", "Daily report"),
                List.of(new PhotoAsset("photo-1", "STEP_1", "photos/working.webp", "Front view", PhotoLayoutSize.MEDIUM)),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        assertEquals(1, result.artifacts().size());
        assertEquals(ArtifactType.DOCX, result.artifacts().get(0).type());
        assertNotNull(result.artifacts().get(0).content());
        assertTrue(result.artifacts().get(0).bytes() > 0);
    }

    @Test
    void simpleEngineFailsClearlyWhenRequestedExporterIsNotConfigured() {
        var engine = new SimpleDocumentEngine();
        var result = engine.generate(new DocumentGenerationRequest(
                "job-export-1",
                "office-1",
                "report-export-1",
                new TemplateSpec("DAILY", 1, "templates/daily.docx", "{}", "{}"),
                Map.of("title", "Daily report"),
                List.of(),
                OutputFormat.PDF));

        assertEquals(GenerationStatus.FAILED, result.status());
        assertEquals("DOCUMENT_EXPORTER_NOT_CONFIGURED", result.errorCode());
        assertTrue(result.errorMessage().contains("DOCX -> PDF"));
    }

    @Test
    void simpleEngineCanReturnDocxAndExportedPdfWhenExporterIsConfigured() {
        var engine = new SimpleDocumentEngine(new DocumentArtifactExportService(List.of(new FakePdfExporter())));
        var result = engine.generate(new DocumentGenerationRequest(
                "job-export-2",
                "office-1",
                "report-export-2",
                new TemplateSpec("DAILY", 1, "templates/daily.docx", "{}", "{}"),
                Map.of("title", "Daily report"),
                List.of(),
                OutputFormat.DOCX_AND_PDF));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        assertEquals(2, result.artifacts().size());
        assertEquals(ArtifactType.DOCX, result.artifacts().get(0).type());
        assertEquals(ArtifactType.PDF, result.artifacts().get(1).type());
        assertEquals("inspection-report-report-export-2.pdf", result.artifacts().get(1).fileName());
        assertNotNull(result.artifacts().get(1).content());
    }

    @Test
    void simpleEngineGeneratesHtmlPreviewWithoutExporter() {
        var engine = new SimpleDocumentEngine();
        var result = engine.generate(new DocumentGenerationRequest(
                "job-export-3",
                "office-1",
                "report-export-3",
                new TemplateSpec("DAILY", 1, "templates/daily.docx", "{}", "{}"),
                Map.of("templateFields", Map.of(
                        "documentTitle", "Daily report",
                        "projectName", "Seoul office")),
                List.of(),
                OutputFormat.HTML));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        assertEquals(1, result.artifacts().size());
        assertEquals(ArtifactType.HTML, result.artifacts().get(0).type());
        assertEquals("inspection-report-report-export-3.html", result.artifacts().get(0).fileName());
        var html = new String(result.artifacts().get(0).content(), StandardCharsets.UTF_8);
        assertTrue(html.contains("<!doctype html>"));
        assertTrue(html.contains("Daily report"));
        assertTrue(html.contains("Seoul office"));
    }

    @Test
    void simpleEngineCanReturnHtmlAndExportedPdfWhenExporterIsConfigured() {
        var engine = new SimpleDocumentEngine(new DocumentArtifactExportService(List.of(new FakePdfExporter())));
        var result = engine.generate(new DocumentGenerationRequest(
                "job-export-4",
                "office-1",
                "report-export-4",
                new TemplateSpec("DAILY", 1, "templates/daily.docx", "{}", "{}"),
                Map.of("templateFields", Map.of("documentTitle", "Preview report")),
                List.of(),
                OutputFormat.HTML_AND_PDF));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        assertEquals(2, result.artifacts().size());
        assertEquals(ArtifactType.HTML, result.artifacts().get(0).type());
        assertEquals(ArtifactType.PDF, result.artifacts().get(1).type());
    }

    @Test
    void docxTemplateEngineGeneratesHtmlPreviewWithPhotoAndChecklistSections() throws Exception {
        var template = docx("""
                Project: ${projectName}
                """);
        var image = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lz7S4QAAAABJRU5ErkJggg==");
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                photo -> Optional.of(new ResolvedPhotoContent(image, "image/png")),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-html-1",
                "office-1",
                "report-html-1",
                new TemplateSpec("DAILY_TEMPLATE", 1, "templates/daily.docx", "{}", "{}"),
                Map.of(
                        "templateFields", Map.of(
                                "documentTitle", "공사감리 미리보기",
                                "projectName", "서초동 근린생활시설"),
                        "layoutSections", Map.of(
                                "photoSection", Map.of(
                                        "type", "PHOTO_TABLE",
                                        "title", "현장 사진",
                                        "photosPerRow", 2,
                                        "fields", List.of(Map.of("label", "설명", "source", "caption"))),
                                "checklistSection", Map.of(
                                        "type", "CHECKLIST_TABLE",
                                        "title", "감리 체크리스트",
                                        "fields", List.of(
                                                Map.of("label", "항목", "source", "label"),
                                                Map.of("label", "결과", "source", "answer.value"),
                                                Map.of("label", "비고", "source", "note")))),
                        "checklistAnswers", List.of(
                                Map.of(
                                        "label", "철근 배근 확인",
                                        "answer", Map.of("value", "적합"),
                                        "note", "도면과 일치"))),
                List.of(new PhotoAsset(
                        "photo-1",
                        "PHOTO_STEP",
                        "photos/photo-1.png",
                        "전면부 확인",
                        PhotoLayoutSize.MEDIUM,
                        "image/png",
                        null)),
                OutputFormat.HTML));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        assertEquals(1, result.artifacts().size());
        assertEquals(ArtifactType.HTML, result.artifacts().get(0).type());
        var html = new String(result.artifacts().get(0).content(), StandardCharsets.UTF_8);
        assertTrue(html.contains("공사감리 미리보기"));
        assertTrue(html.contains("서초동 근린생활시설"));
        assertTrue(html.contains("현장 사진"));
        assertTrue(html.contains("data:image/png;base64,"));
        assertTrue(html.contains("전면부 확인"));
        assertTrue(html.contains("감리 체크리스트"));
        assertTrue(html.contains("철근 배근 확인"));
        assertTrue(html.contains("적합"));
        assertTrue(html.contains("도면과 일치"));
    }

    @Test
    void docxTemplateEngineBindsPlaceholdersFromPayload() throws Exception {
        var template = docx("""
                Project: ${report.title}
                Weather: ${weather}
                Template: ${templateCode} v${templateVersion}
                Unknown: ${unknownValue}
                Escaped: ${specialText}
                """);
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-2",
                "office-1",
                "report-2",
                new TemplateSpec("DAILY_TEMPLATE", 3, "templates/daily.docx", "{}", "{}"),
                Map.of(
                        "report", Map.of("title", "Daily report"),
                        "steps", Map.of("BASIC", Map.of("payload", Map.of("weather", "Clear"))),
                        "specialText", "A&B <C>"),
                List.of(),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        var documentXml = documentXml(result.artifacts().get(0).content());
        assertTrue(documentXml.contains("Project: Daily report"));
        assertTrue(documentXml.contains("Weather: Clear"));
        assertTrue(documentXml.contains("Template: DAILY_TEMPLATE v3"));
        assertTrue(documentXml.contains("Unknown: ${unknownValue}"));
        assertTrue(documentXml.contains("Escaped: A&amp;B &lt;C&gt;"));
    }

    @Test
    void docxTemplateEnginePrefersExplicitTemplateFieldAliases() throws Exception {
        var template = docx("""
                Project name: ${projectName}
                Inspection date: ${inspectionDate}
                Weather: ${weather}
                """);
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-3",
                "office-1",
                "report-3",
                new TemplateSpec("DAILY_TEMPLATE", 1, "templates/daily.docx", "{}", "{}"),
                Map.of(
                        "project", Map.of("name", "Internal project name"),
                        "steps", Map.of("BASIC_INFO", Map.of("payload", Map.of(
                                "inspectionDate", "2026-05-23",
                                "weather", "Clear"))),
                        "templateFields", Map.of(
                                "projectName", "Mapped project name",
                                "inspectionDate", "2026-05-24",
                                "weather", "Mapped weather")),
                List.of(),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        var documentXml = documentXml(result.artifacts().get(0).content());
        assertTrue(documentXml.contains("Project name: Mapped project name"));
        assertTrue(documentXml.contains("Inspection date: 2026-05-24"));
        assertTrue(documentXml.contains("Weather: Mapped weather"));
    }

    @Test
    void docxTemplateEngineBindsPlaceholderSplitAcrossWordTextNodes() throws Exception {
        var template = docxWithBodyXml("""
                <w:p>
                  <w:r><w:t>Project: ${pro</w:t></w:r>
                  <w:r><w:t>ject</w:t></w:r>
                  <w:r><w:t>Name}</w:t></w:r>
                  <w:r><w:t> / Weather: ${weather}</w:t></w:r>
                </w:p>
                """);
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-4",
                "office-1",
                "report-4",
                new TemplateSpec("DAILY_TEMPLATE", 1, "templates/daily.docx", "{}", "{}"),
                Map.of("templateFields", Map.of(
                        "projectName", "Mapped project name",
                        "weather", "Cloudy")),
                List.of(),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        var documentXml = documentXml(result.artifacts().get(0).content());
        assertTrue(documentXml.contains("Project: Mapped project name"));
        assertTrue(documentXml.contains("Weather: Cloudy"));
        assertTrue(!documentXml.contains("${projectName}"));
        assertTrue(!documentXml.contains("${pro"));
    }

    @Test
    void docxTemplateEngineReplacesPhotoTablePlaceholderWithTableAndImage() throws Exception {
        var template = docxWithBodyXml("""
                <w:p><w:r><w:t>${photoSection}</w:t></w:r></w:p>
                """);
        var image = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lz7S4QAAAABJRU5ErkJggg==");
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                photo -> Optional.of(new ResolvedPhotoContent(image, "image/png")),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-5",
                "office-1",
                "report-5",
                new TemplateSpec("DAILY_TEMPLATE", 1, "templates/daily.docx", "{}", "{}"),
                Map.of("layoutSections", Map.of(
                        "photoSection", Map.of(
                                "type", "PHOTO_TABLE",
                                "title", "Site Photos"))),
                List.of(new PhotoAsset(
                        "photo-1",
                        "BASIC_INFO",
                        "photos/photo-1.png",
                        "Front view",
                        PhotoLayoutSize.MEDIUM,
                        "image/png",
                        null)),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        var content = result.artifacts().get(0).content();
        var documentXml = documentXml(content);
        assertTrue(documentXml.contains("<w:tbl>"));
        assertTrue(documentXml.contains("Site Photos"));
        assertTrue(documentXml.contains("Photo ID: photo-1"));
        assertTrue(documentXml.contains("Caption: Front view"));
        assertTrue(!documentXml.contains("${photoSection}"));
        assertTrue(zipEntry(content, "word/_rels/document.xml.rels").contains("rIdArchDoxImage1"));
        assertTrue(zipEntry(content, "[Content_Types].xml").contains("Extension=\"png\""));
        assertNotNull(zipEntryBytes(content, "word/media/archdox-photo-1.png"));
    }

    @Test
    void docxTemplateEngineSupportsPhotoGridLayoutOptions() throws Exception {
        var template = docxWithBodyXml("""
                <w:p><w:r><w:t>${photoSection}</w:t></w:r></w:p>
                """);
        var image = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lz7S4QAAAABJRU5ErkJggg==");
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                photo -> Optional.of(new ResolvedPhotoContent(image, "image/png")),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-6",
                "office-1",
                "report-6",
                new TemplateSpec("DAILY_TEMPLATE", 1, "templates/daily.docx", "{}", "{}"),
                Map.of("layoutSections", Map.of(
                        "photoSection", Map.of(
                                "type", "PHOTO_TABLE",
                                "title", "Two Column Photos",
                                "photosPerRow", 2,
                                "imageSize", "THUMBNAIL",
                                "fields", List.of(Map.of("label", "Caption", "source", "caption"))))),
                List.of(
                        new PhotoAsset("photo-1", "BASIC_INFO", "photos/photo-1.png", "Front view", PhotoLayoutSize.MEDIUM, "image/png", null),
                        new PhotoAsset("photo-2", "BASIC_INFO", "photos/photo-2.png", "Rear view", PhotoLayoutSize.MEDIUM, "image/png", null),
                        new PhotoAsset("photo-3", "BASIC_INFO", "photos/photo-3.png", "Detail view", PhotoLayoutSize.MEDIUM, "image/png", null)),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        var content = result.artifacts().get(0).content();
        var documentXml = documentXml(content);
        assertTrue(documentXml.contains("Two Column Photos"));
        assertTrue(documentXml.contains("<w:gridCol w:w=\"4500\"/><w:gridCol w:w=\"4500\"/>"));
        assertTrue(documentXml.contains("Caption: Front view"));
        assertTrue(documentXml.contains("Caption: Rear view"));
        assertTrue(documentXml.contains("Caption: Detail view"));
        assertTrue(!documentXml.contains("Storage: photos/photo-1.png"));
        assertTrue(documentXml.contains("cx=\"1371600\" cy=\"1028700\""));
        assertNotNull(zipEntryBytes(content, "word/media/archdox-photo-3.png"));
    }

    @Test
    void docxTemplateEngineReplacesChecklistTablePlaceholderWithTable() throws Exception {
        var template = docxWithBodyXml("""
                <w:p><w:r><w:t>${checklistSection}</w:t></w:r></w:p>
                """);
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-7",
                "office-1",
                "report-7",
                new TemplateSpec("DAILY_TEMPLATE", 1, "templates/daily.docx", "{}", "{}"),
                Map.of(
                        "layoutSections", Map.of(
                                "checklistSection", Map.of(
                                        "type", "CHECKLIST_TABLE",
                                        "title", "Checklist",
                                        "fields", List.of(
                                                Map.of("label", "Code", "source", "itemCode"),
                                                Map.of("label", "Item", "source", "label"),
                                                Map.of("label", "Result", "source", "answer.value"),
                                                Map.of("label", "Note", "source", "note")))),
                        "checklistAnswers", List.of(
                                Map.of(
                                        "itemCode", "CHK-1",
                                        "label", "Guard rail",
                                        "answer", Map.of("value", "OK"),
                                        "note", "Installed"),
                                Map.of(
                                        "itemCode", "CHK-2",
                                        "label", "Opening",
                                        "answer", Map.of("value", "NG"),
                                        "note", "Needs cover"))),
                List.of(),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        var documentXml = documentXml(result.artifacts().get(0).content());
        assertTrue(documentXml.contains("<w:tbl>"));
        assertTrue(documentXml.contains("Checklist"));
        assertTrue(documentXml.contains("Guard rail"));
        assertTrue(documentXml.contains("OK"));
        assertTrue(documentXml.contains("Needs cover"));
        assertTrue(!documentXml.contains("${checklistSection}"));
    }

    @Test
    void docxTemplateEngineReplacesChecklistPhotoTablePlaceholderWithTable() throws Exception {
        var template = docxWithBodyXml("""
                <w:p><w:r><w:t>${checklistPhotoSection}</w:t></w:r></w:p>
                """);
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-7-photo",
                "office-1",
                "report-7",
                new TemplateSpec("DAILY_TEMPLATE", 1, "templates/daily.docx", "{}", "{}"),
                Map.of(
                        "layoutSections", Map.of(
                                "checklistPhotoSection", Map.of(
                                        "type", "CHECKLIST_PHOTO_TABLE",
                                        "title", "Checklist Photos",
                                        "fields", List.of(
                                                Map.of("label", "Code", "source", "itemCode"),
                                                Map.of("label", "Item", "source", "label"),
                                                Map.of("label", "Photos", "source", "photoCount"),
                                                Map.of("label", "Photo IDs", "source", "photoIds")))),
                        "checklistPhotos", List.of(
                                Map.of(
                                        "itemCode", "CHK-1",
                                        "label", "Guard rail",
                                        "photoCount", 2,
                                        "photoIds", List.of(100, 101)))),
                List.of(),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        var documentXml = documentXml(result.artifacts().get(0).content());
        assertTrue(documentXml.contains("<w:tbl>"));
        assertTrue(documentXml.contains("Checklist Photos"));
        assertTrue(documentXml.contains("Guard rail"));
        assertTrue(documentXml.contains("100"));
        assertTrue(documentXml.contains("101"));
        assertTrue(!documentXml.contains("${checklistPhotoSection}"));
    }

    @Test
    void docxTemplateEngineSupportsRichTableStyleOptions() throws Exception {
        var template = docxWithBodyXml("""
                <w:p><w:r><w:t>${checklistSection}</w:t></w:r></w:p>
                """);
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-8",
                "office-1",
                "report-8",
                new TemplateSpec("DAILY_TEMPLATE", 1, "templates/daily.docx", "{}", "{}"),
                Map.of("layoutSections", Map.of(
                        "checklistSection", Map.of(
                                "type", "CHECKLIST_TABLE",
                                "title", "Hidden Checklist Title",
                                "includeTitle", false,
                                "emptyText", "No checklist answers saved.",
                                "tableStyle", "ArchDoxInspectionTable",
                                "borderColor", "#C9A227",
                                "headerFill", "FFF2CC",
                                "fields", List.of(
                                        Map.of("label", "Code", "source", "itemCode", "width", 1800),
                                        Map.of("label", "Item", "source", "label", "width", 5200),
                                        Map.of("label", "Result", "source", "answer.value", "width", 2000))))),
                List.of(),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        var documentXml = documentXml(result.artifacts().get(0).content());
        assertTrue(documentXml.contains("<w:tblStyle w:val=\"ArchDoxInspectionTable\"/>"));
        assertTrue(documentXml.contains("w:color=\"C9A227\""));
        assertTrue(documentXml.contains("<w:shd w:fill=\"FFF2CC\"/>"));
        assertTrue(documentXml.contains("<w:gridCol w:w=\"1800\"/><w:gridCol w:w=\"5200\"/><w:gridCol w:w=\"2000\"/>"));
        assertTrue(documentXml.contains("No checklist answers saved."));
        assertTrue(!documentXml.contains("Hidden Checklist Title"));
        assertTrue(!documentXml.contains("${checklistSection}"));
    }

    @Test
    void docxTemplateEngineFallsBackWhenTemplateContentIsOptional() {
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.empty(),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-3",
                "office-1",
                "report-4",
                new TemplateSpec("DAILY", 1, "templates/missing.docx", "{}", "{}"),
                Map.of("title", "Daily report"),
                List.of(),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        assertEquals(ArtifactType.DOCX, result.artifacts().get(0).type());
        assertNotNull(result.artifacts().get(0).content());
    }

    @Test
    void docxTemplateEngineFailsWhenRequiredTemplateContentIsMissing() {
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.empty(),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-required-template",
                "office-1",
                "report-required-template",
                new TemplateSpec(
                        "DAILY",
                        1,
                        "templates/missing.docx",
                        "{}",
                        "{}",
                        null,
                        true),
                Map.of("title", "Daily report"),
                List.of(),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.FAILED, result.status());
        assertEquals("DOCUMENT_TEMPLATE_CONTENT_NOT_FOUND", result.errorCode());
        assertTrue(result.artifacts().isEmpty());
    }

    private byte[] docx(String bodyText) throws Exception {
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
                        <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                        <w:sectPr/>
                      </w:body>
                    </w:document>
                    """.formatted(escapeXml(bodyText)));
            zip.finish();
            return output.toByteArray();
        }
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

    private String documentXml(byte[] docx) throws Exception {
        return zipEntry(docx, "word/document.xml");
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

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static final class FakePdfExporter implements DocumentArtifactExporter {
        @Override
        public boolean supports(ArtifactType sourceType, ArtifactType targetType) {
            return sourceType == ArtifactType.DOCX && targetType == ArtifactType.PDF;
        }

        @Override
        public DocumentExportResult export(DocumentExportRequest request) {
            var content = "fake-pdf".getBytes(StandardCharsets.UTF_8);
            return DocumentExportResult.completed(new GeneratedArtifact(
                    ArtifactType.PDF,
                    "inspection-report-" + request.reportId() + ".pdf",
                    "documents/jobs/" + request.jobId() + "/inspection-report-" + request.reportId() + ".pdf",
                    content.length,
                    "fake-sha256",
                    content));
        }
    }
}

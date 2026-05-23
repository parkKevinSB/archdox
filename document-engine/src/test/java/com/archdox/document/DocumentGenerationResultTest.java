package com.archdox.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
    void docxTemplateEngineFallsBackWhenTemplateIsMissing() {
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.empty(),
                new SimpleDocumentEngine());

        var result = engine.generate(new DocumentGenerationRequest(
                "job-3",
                "office-1",
                "report-3",
                new TemplateSpec("DAILY", 1, "templates/missing.docx", "{}", "{}"),
                Map.of("title", "Daily report"),
                List.of(),
                OutputFormat.DOCX));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        assertEquals(ArtifactType.DOCX, result.artifacts().get(0).type());
        assertNotNull(result.artifacts().get(0).content());
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

    private void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String documentXml(byte[] docx) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(docx), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return "";
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

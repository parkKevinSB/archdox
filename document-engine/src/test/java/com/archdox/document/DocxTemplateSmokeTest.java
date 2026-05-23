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
        var outputDirectory = Path.of("build", "archdox-smoke");
        Files.createDirectories(outputDirectory);
        Files.write(outputDirectory.resolve("realistic-inspection-smoke.docx"), content);
    }
}

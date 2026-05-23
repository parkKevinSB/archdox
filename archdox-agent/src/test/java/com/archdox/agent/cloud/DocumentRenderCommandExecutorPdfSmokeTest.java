package com.archdox.agent.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.archdox.agent.document.AgentDocumentStore;
import com.archdox.document.ArtifactType;
import com.archdox.document.DocxTemplateDocumentEngine;
import com.archdox.document.DocumentArtifactExportService;
import com.archdox.document.LibreOfficeCommandResult;
import com.archdox.document.LibreOfficeDocumentArtifactExporter;
import com.archdox.document.LibreOfficePdfExportOptions;
import com.archdox.document.ProcessLibreOfficeCommandRunner;
import com.archdox.document.SimpleDocumentEngine;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentRenderCommandExecutorPdfSmokeTest {
    private static final String SMOKE_PROPERTY = "archdox.agent.pdf-smoke.enabled";

    @Test
    void rendersDocxAndPdfThroughAgentCommandPath(@TempDir Path tempDir) throws Exception {
        assumeTrue(Boolean.getBoolean(SMOKE_PROPERTY), "Enable with -D" + SMOKE_PROPERTY + "=true");

        var soffice = libreOfficePath();
        var probe = probeLibreOffice(soffice);
        assumeTrue(!probe.timedOut() && probe.exitCode() == 0, "LibreOffice is not available: " + probe.output());

        var properties = new ArchDoxAgentProperties();
        properties.setLocalStorageRoot(tempDir.toString());

        var template = templateDocx();
        var exportService = new DocumentArtifactExportService(List.of(
                new LibreOfficeDocumentArtifactExporter(new LibreOfficePdfExportOptions(soffice, 60000))));
        var engine = new DocxTemplateDocumentEngine(
                spec -> Optional.of(template),
                new SimpleDocumentEngine(exportService),
                exportService);
        var executor = new DocumentRenderCommandExecutor(engine, new AgentDocumentStore(properties));
        var payload = Map.<String, Object>of(
                "documentJobId", "job-pdf-smoke",
                "officeId", "office-smoke",
                "reportId", "report-pdf-smoke",
                "outputFormat", "DOCX_AND_PDF",
                "inputSnapshot", Map.of(
                        "report", Map.of("title", "Agent PDF smoke"),
                        "templateFields", Map.of(
                                "projectName", "ArchDox Runtime Project",
                                "inspectionDate", "2026-05-23",
                                "inspectorName", "Runtime Inspector")),
                "template", Map.of(
                        "templateCode", "AGENT_PDF_SMOKE",
                        "version", 1,
                        "storageRef", "templates/agent-pdf-smoke.docx",
                        "schemaJson", "{}",
                        "composePolicyJson", "{}"),
                "photos", List.of(),
                "resultStorageKind", "ARCHDOX_AGENT");

        var result = executor.execute(new CloudInboundMessage(
                "COMMAND",
                null,
                null,
                null,
                9901L,
                "GENERATE_DOCUMENT",
                payload,
                null));

        @SuppressWarnings("unchecked")
        var artifacts = (List<Map<String, Object>>) result.get("artifacts");
        assertEquals(2, artifacts.size());

        var docx = artifact(artifacts, ArtifactType.DOCX);
        var pdf = artifact(artifacts, ArtifactType.PDF);
        assertEquals("ARCHDOX_AGENT", docx.get("storageKind"));
        assertEquals("ARCHDOX_AGENT", pdf.get("storageKind"));
        assertEquals("application/pdf", pdf.get("mimeType"));

        var docxPath = tempDir.resolve(String.valueOf(docx.get("storageRef")));
        var pdfPath = tempDir.resolve(String.valueOf(pdf.get("storageRef")));
        assertTrue(Files.size(docxPath) > 0);
        assertTrue(Files.size(pdfPath) > 4);
        assertTrue(new String(Files.readAllBytes(pdfPath), 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    private String libreOfficePath() {
        var property = System.getProperty("archdox.agent.pdf-smoke.soffice");
        if (property != null && !property.isBlank()) {
            return property;
        }
        var env = System.getenv("DOCUMENT_EXPORT_LIBREOFFICE_PATH");
        return env == null || env.isBlank() ? "soffice" : env;
    }

    private LibreOfficeCommandResult probeLibreOffice(String executablePath) throws Exception {
        return new ProcessLibreOfficeCommandRunner().run(
                List.of(executablePath, "--version"),
                Duration.ofSeconds(5));
    }

    private Map<String, Object> artifact(List<Map<String, Object>> artifacts, ArtifactType type) {
        return artifacts.stream()
                .filter(artifact -> type.name().equals(artifact.get("artifactType")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(type + " artifact not found"));
    }

    private byte[] templateDocx() throws Exception {
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
                        <w:p><w:r><w:t>ArchDox Agent PDF Smoke</w:t></w:r></w:p>
                        <w:p><w:r><w:t>Project: ${projectName}</w:t></w:r></w:p>
                        <w:p><w:r><w:t>Date: ${inspectionDate}</w:t></w:r></w:p>
                        <w:p><w:r><w:t>Inspector: ${inspectorName}</w:t></w:r></w:p>
                        <w:sectPr/>
                      </w:body>
                    </w:document>
                    """);
            zip.finish();
            return output.toByteArray();
        }
    }

    private void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

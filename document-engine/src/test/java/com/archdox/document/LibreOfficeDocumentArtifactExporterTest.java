package com.archdox.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LibreOfficeDocumentArtifactExporterTest {
    @Test
    void exportsDocxArtifactToPdfWithLibreOfficeCommand() {
        var exporter = new LibreOfficeDocumentArtifactExporter(
                new LibreOfficePdfExportOptions("fake-soffice", 1234),
                (command, timeout) -> {
                    assertEquals("fake-soffice", command.get(0));
                    assertTrue(command.stream().anyMatch(argument ->
                            argument.startsWith("-env:UserInstallation=file:")));
                    assertTrue(command.contains("--headless"));
                    assertTrue(command.contains("--invisible"));
                    assertTrue(command.contains("--norestore"));
                    assertTrue(command.contains("--convert-to"));
                    assertEquals(Duration.ofMillis(1234), timeout);
                    var outputDir = Path.of(command.get(command.indexOf("--outdir") + 1));
                    Files.write(outputDir.resolve("inspection-report-report-1.pdf"), "pdf-bytes".getBytes(StandardCharsets.UTF_8));
                    return new LibreOfficeCommandResult(0, "converted", false);
                });

        var result = exporter.export(exportRequest(docxArtifact("inspection-report-report-1.docx")));

        assertTrue(result.isCompleted());
        assertEquals(ArtifactType.PDF, result.artifact().type());
        assertEquals("inspection-report-report-1.pdf", result.artifact().fileName());
        assertEquals("documents/jobs/job-1/inspection-report-report-1.pdf", result.artifact().storageRef());
        assertEquals("pdf-bytes", new String(result.artifact().content(), StandardCharsets.UTF_8));
        assertNotNull(result.artifact().sha256());
    }

    @Test
    void returnsTimeoutFailureWhenLibreOfficeDoesNotFinish() {
        var exporter = new LibreOfficeDocumentArtifactExporter(
                new LibreOfficePdfExportOptions("fake-soffice", 50),
                (command, timeout) -> new LibreOfficeCommandResult(-1, "still running", true));

        var result = exporter.export(exportRequest(docxArtifact("report.docx")));

        assertTrue(!result.isCompleted());
        assertEquals("DOCUMENT_PDF_EXPORT_TIMEOUT", result.errorCode());
        assertTrue(result.errorMessage().contains("50 ms"));
    }

    @Test
    void returnsFailureWhenLibreOfficeExitsNonZero() {
        var exporter = new LibreOfficeDocumentArtifactExporter(
                LibreOfficePdfExportOptions.defaults(),
                (command, timeout) -> new LibreOfficeCommandResult(7, "conversion failed", false));

        var result = exporter.export(exportRequest(docxArtifact("report.docx")));

        assertTrue(!result.isCompleted());
        assertEquals("DOCUMENT_PDF_EXPORT_FAILED", result.errorCode());
        assertTrue(result.errorMessage().contains("exit code 7"));
    }

    @Test
    void returnsFailureWhenLibreOfficeProducesNoPdf() {
        var exporter = new LibreOfficeDocumentArtifactExporter(
                LibreOfficePdfExportOptions.defaults(),
                (command, timeout) -> new LibreOfficeCommandResult(0, "converted but no output", false));

        var result = exporter.export(exportRequest(docxArtifact("report.docx")));

        assertTrue(!result.isCompleted());
        assertEquals("DOCUMENT_PDF_EXPORT_NO_OUTPUT", result.errorCode());
    }

    @Test
    void returnsNotAvailableWhenExecutableCannotStart() {
        var exporter = new LibreOfficeDocumentArtifactExporter(
                new LibreOfficePdfExportOptions("missing-soffice", 1000),
                (command, timeout) -> {
                    throw new IOException("Cannot run program");
                });

        var result = exporter.export(exportRequest(docxArtifact("report.docx")));

        assertTrue(!result.isCompleted());
        assertEquals("DOCUMENT_PDF_EXPORTER_NOT_AVAILABLE", result.errorCode());
        assertTrue(result.errorMessage().contains("missing-soffice"));
    }

    @Test
    void rejectsSourceArtifactWithoutContent() {
        var exporter = new LibreOfficeDocumentArtifactExporter(LibreOfficePdfExportOptions.defaults());

        var result = exporter.export(exportRequest(new GeneratedArtifact(
                ArtifactType.DOCX,
                "report.docx",
                "documents/jobs/job-1/report.docx",
                10,
                "sha")));

        assertTrue(!result.isCompleted());
        assertEquals("DOCUMENT_PDF_EXPORT_NO_SOURCE_CONTENT", result.errorCode());
    }

    private DocumentExportRequest exportRequest(GeneratedArtifact sourceArtifact) {
        return new DocumentExportRequest(
                "job-1",
                "report-1",
                new TemplateSpec("DAILY", 1, "templates/daily.docx", "{}", "{}"),
                sourceArtifact,
                ArtifactType.PDF,
                Map.of());
    }

    private GeneratedArtifact docxArtifact(String fileName) {
        var content = "fake-docx".getBytes(StandardCharsets.UTF_8);
        return new GeneratedArtifact(
                ArtifactType.DOCX,
                fileName,
                "documents/jobs/job-1/" + fileName,
                content.length,
                "source-sha",
                content);
    }
}

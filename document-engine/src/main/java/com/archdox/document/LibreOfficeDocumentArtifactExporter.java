package com.archdox.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public class LibreOfficeDocumentArtifactExporter implements DocumentArtifactExporter {
    private final LibreOfficePdfExportOptions options;
    private final LibreOfficeCommandRunner commandRunner;

    public LibreOfficeDocumentArtifactExporter(LibreOfficePdfExportOptions options) {
        this(options, new ProcessLibreOfficeCommandRunner());
    }

    public LibreOfficeDocumentArtifactExporter(
            LibreOfficePdfExportOptions options,
            LibreOfficeCommandRunner commandRunner
    ) {
        this.options = options == null ? LibreOfficePdfExportOptions.defaults() : options;
        this.commandRunner = commandRunner == null ? new ProcessLibreOfficeCommandRunner() : commandRunner;
    }

    @Override
    public boolean supports(ArtifactType sourceType, ArtifactType targetType) {
        return sourceType == ArtifactType.DOCX && targetType == ArtifactType.PDF;
    }

    @Override
    public DocumentExportResult export(DocumentExportRequest request) {
        if (request.sourceArtifact().content() == null || request.sourceArtifact().content().length == 0) {
            return DocumentExportResult.failed(
                    "DOCUMENT_PDF_EXPORT_NO_SOURCE_CONTENT",
                    "DOCX source artifact content is required for PDF export.");
        }
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("archdox-pdf-export-");
            var sourceFileName = sourceDocxFileName(request.sourceArtifact());
            var sourcePath = workspace.resolve(sourceFileName);
            var outputDirectory = workspace.resolve("out");
            Files.createDirectories(outputDirectory);
            Files.write(sourcePath, request.sourceArtifact().content());

            var command = List.of(
                    options.executablePath(),
                    "--headless",
                    "--nologo",
                    "--nofirststartwizard",
                    "--convert-to",
                    "pdf",
                    "--outdir",
                    outputDirectory.toString(),
                    sourcePath.toString());
            var result = commandRunner.run(command, Duration.ofMillis(options.timeoutMs()));
            if (result.timedOut()) {
                return DocumentExportResult.failed(
                        "DOCUMENT_PDF_EXPORT_TIMEOUT",
                        "LibreOffice PDF export timed out after %d ms.".formatted(options.timeoutMs()));
            }
            if (result.exitCode() != 0) {
                return DocumentExportResult.failed(
                        "DOCUMENT_PDF_EXPORT_FAILED",
                        "LibreOffice PDF export failed with exit code %d. %s".formatted(
                                result.exitCode(),
                                trimOutput(result.output())));
            }

            var pdfPath = outputDirectory.resolve(baseName(sourceFileName) + ".pdf");
            if (!Files.exists(pdfPath)) {
                return DocumentExportResult.failed(
                        "DOCUMENT_PDF_EXPORT_NO_OUTPUT",
                        "LibreOffice PDF export completed but no PDF output was produced.");
            }
            var content = Files.readAllBytes(pdfPath);
            var fileName = outputPdfFileName(request.sourceArtifact(), request.reportId());
            return DocumentExportResult.completed(new GeneratedArtifact(
                    ArtifactType.PDF,
                    fileName,
                    outputStorageRef(request.sourceArtifact(), request.jobId(), fileName),
                    content.length,
                    sha256(content),
                    content));
        } catch (IOException ex) {
            return DocumentExportResult.failed(
                    "DOCUMENT_PDF_EXPORTER_NOT_AVAILABLE",
                    "LibreOffice executable is not available or cannot run: %s. %s".formatted(
                            options.executablePath(),
                            ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return DocumentExportResult.failed(
                    "DOCUMENT_PDF_EXPORT_INTERRUPTED",
                    "LibreOffice PDF export was interrupted.");
        } catch (RuntimeException ex) {
            return DocumentExportResult.failed(
                    "DOCUMENT_PDF_EXPORT_FAILED",
                    ex.getMessage());
        } finally {
            deleteWorkspaceQuietly(workspace);
        }
    }

    private String sourceDocxFileName(GeneratedArtifact artifact) {
        var fileName = safeFileName(artifact.fileName(), "source.docx");
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            return fileName;
        }
        return fileName + ".docx";
    }

    private String outputPdfFileName(GeneratedArtifact sourceArtifact, String reportId) {
        var sourceName = safeFileName(sourceArtifact.fileName(), "inspection-report-" + sanitizeFileName(reportId) + ".docx");
        return baseName(sourceName) + ".pdf";
    }

    private String outputStorageRef(GeneratedArtifact sourceArtifact, String jobId, String fileName) {
        var storageRef = sourceArtifact.storageRef();
        if (storageRef != null && !storageRef.isBlank()) {
            var slash = storageRef.lastIndexOf('/');
            var parent = slash < 0 ? "" : storageRef.substring(0, slash + 1);
            return parent + fileName;
        }
        return "documents/jobs/" + sanitizeFileName(jobId) + "/" + fileName;
    }

    private String baseName(String fileName) {
        var name = safeFileName(fileName, "document");
        var dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private String safeFileName(String fileName, String fallback) {
        var value = fileName == null || fileName.isBlank() ? fallback : fileName.trim();
        return value.replace('\\', '/').replaceAll(".*/", "").replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String trimOutput(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        var trimmed = output.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private void deleteWorkspaceQuietly(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary export workspaces are best-effort cleanup.
                }
            });
        } catch (IOException ignored) {
            // Temporary export workspaces are best-effort cleanup.
        }
    }
}

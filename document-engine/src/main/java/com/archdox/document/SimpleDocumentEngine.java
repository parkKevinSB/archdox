package com.archdox.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SimpleDocumentEngine implements DocumentEngine {
    private final DocumentArtifactExportService exportService;
    private final HtmlPreviewDocumentRenderer htmlRenderer;

    public SimpleDocumentEngine() {
        this(DocumentArtifactExportService.disabled());
    }

    public SimpleDocumentEngine(DocumentArtifactExportService exportService) {
        this(exportService, new HtmlPreviewDocumentRenderer());
    }

    public SimpleDocumentEngine(
            DocumentArtifactExportService exportService,
            HtmlPreviewDocumentRenderer htmlRenderer
    ) {
        this.exportService = exportService == null ? DocumentArtifactExportService.disabled() : exportService;
        this.htmlRenderer = htmlRenderer == null ? new HtmlPreviewDocumentRenderer() : htmlRenderer;
    }

    @Override
    public DocumentGenerationResult generate(DocumentGenerationRequest request) {
        try {
            var fileName = "inspection-report-" + sanitizeFileName(request.reportId()) + ".docx";
            var storageRef = "documents/jobs/" + sanitizeFileName(request.jobId()) + "/" + fileName;
            var content = createDocx(renderLines(request));
            var artifact = new GeneratedArtifact(
                    ArtifactType.DOCX,
                    fileName,
                    storageRef,
                    content.length,
                    sha256(content),
                    content);
            return DocumentGenerationArtifacts.completeFromDocx(request, artifact, exportService, htmlRenderer);
        } catch (IOException | RuntimeException ex) {
            return DocumentGenerationResult.failed(request.jobId(), "DOCUMENT_RENDER_FAILED", ex.getMessage());
        }
    }

    private List<String> renderLines(DocumentGenerationRequest request) {
        var lines = new ArrayList<String>();
        lines.add("ArchDox Inspection Report");
        lines.add("Generated At: " + OffsetDateTime.now());
        lines.add("Job ID: " + request.jobId());
        lines.add("Office: " + request.officeCode());
        lines.add("Report ID: " + request.reportId());
        lines.add("Template: " + request.template().templateCode() + " v" + request.template().version());
        lines.add("");
        lines.add("Report Data");
        appendValue(lines, "payload", request.payload());
        lines.add("");
        lines.add("Photos");
        if (request.photos() == null || request.photos().isEmpty()) {
            lines.add("photos: none");
        } else {
            request.photos().forEach(photo -> lines.add(
                    "photoId=%s, checklistItemKey=%s, storageRef=%s, caption=%s, layoutSize=%s"
                            .formatted(
                                    photo.photoId(),
                                    valueOrBlank(photo.checklistItemKey()),
                                    valueOrBlank(photo.storageRef()),
                                    valueOrBlank(photo.caption()),
                                    photo.layoutSize())));
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    private void appendValue(List<String> lines, String prefix, Object value) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> appendValue(lines, prefix + "." + key, nested));
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                appendValue(lines, prefix + "[" + i + "]", list.get(i));
            }
            return;
        }
        lines.add(prefix + ": " + valueOrBlank((Object) value));
    }

    private byte[] createDocx(List<String> lines) throws IOException {
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
            put(zip, "word/document.xml", documentXml(lines));
            zip.finish();
            return output.toByteArray();
        }
    }

    private String documentXml(List<String> lines) {
        var body = new StringBuilder();
        for (var line : lines) {
            body.append("<w:p><w:r><w:t>")
                    .append(escapeXml(line))
                    .append("</w:t></w:r></w:p>");
        }
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    %s
                    <w:sectPr/>
                  </w:body>
                </w:document>
                """.formatted(body);
    }

    private void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String escapeXml(String value) {
        return valueOrBlank(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String valueOrBlank(Object value) {
        return value == null ? "" : value.toString();
    }
}

package com.archdox.document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DocxTemplateDocumentEngine implements DocumentEngine {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_.\\-\\[\\]]+)}");
    private static final Pattern WORD_TEXT_NODE_PATTERN = Pattern.compile("(<w:t(?:\\s+[^>]*)?>)(.*?)(</w:t>)",
            Pattern.DOTALL);
    private static final Pattern WORD_PARAGRAPH_PATTERN = Pattern.compile("<w:p(?:\\s+[^>]*)?>.*?</w:p>",
            Pattern.DOTALL);

    private final TemplateContentResolver templateContentResolver;
    private final PhotoContentResolver photoContentResolver;
    private final DocumentEngine fallback;

    public DocxTemplateDocumentEngine(TemplateContentResolver templateContentResolver, DocumentEngine fallback) {
        this(templateContentResolver, photo -> Optional.empty(), fallback);
    }

    public DocxTemplateDocumentEngine(
            TemplateContentResolver templateContentResolver,
            PhotoContentResolver photoContentResolver,
            DocumentEngine fallback
    ) {
        this.templateContentResolver = templateContentResolver;
        this.photoContentResolver = photoContentResolver;
        this.fallback = fallback;
    }

    @Override
    public DocumentGenerationResult generate(DocumentGenerationRequest request) {
        if (request.outputFormat() == OutputFormat.PDF || request.outputFormat() == OutputFormat.DOCX_AND_PDF) {
            return DocumentGenerationResult.failed(
                    request.jobId(),
                    "UNSUPPORTED_OUTPUT_FORMAT",
                    "PDF conversion is not implemented in the MVP document engine");
        }

        try {
            Optional<byte[]> templateContent = templateContentResolver.resolve(request.template());
            if (templateContent.isEmpty()) {
                return fallback.generate(request);
            }

            var fileName = "inspection-report-" + sanitizeFileName(request.reportId()) + ".docx";
            var storageRef = "documents/jobs/" + sanitizeFileName(request.jobId()) + "/" + fileName;
            var content = bindTemplate(templateContent.get(), request);
            var artifact = new GeneratedArtifact(
                    ArtifactType.DOCX,
                    fileName,
                    storageRef,
                    content.length,
                    sha256(content),
                    content);
            return DocumentGenerationResult.completed(request.jobId(), List.of(artifact));
        } catch (IOException ex) {
            return DocumentGenerationResult.failed(request.jobId(), "TEMPLATE_BINDING_FAILED", ex.getMessage());
        } catch (RuntimeException ex) {
            return DocumentGenerationResult.failed(request.jobId(), "DOCUMENT_RENDER_FAILED", ex.getMessage());
        }
    }

    private byte[] bindTemplate(byte[] templateContent, DocumentGenerationRequest request) throws IOException {
        var bindings = buildBindings(request);
        var context = new DocxRenderContext(request, bindings);
        var entries = new LinkedHashMap<String, byte[]>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(templateContent), StandardCharsets.UTF_8)) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                entries.put(entry.getName(), input.readAllBytes());
            }
        }

        if (entries.containsKey("word/document.xml")) {
            entries.put("word/document.xml", renderMainDocumentXml(
                    new String(entries.get("word/document.xml"), StandardCharsets.UTF_8),
                    context).getBytes(StandardCharsets.UTF_8));
        }
        entries.replaceAll((name, bytes) -> {
            if (shouldBind(name) && !"word/document.xml".equals(name)) {
                return replacePlaceholders(new String(bytes, StandardCharsets.UTF_8), bindings).getBytes(StandardCharsets.UTF_8);
            }
            return bytes;
        });
        if (!context.relationships().isEmpty() || entries.containsKey("word/_rels/document.xml.rels")) {
            entries.put("word/_rels/document.xml.rels", updateDocumentRelationships(
                    entries.get("word/_rels/document.xml.rels"),
                    context.relationships()).getBytes(StandardCharsets.UTF_8));
        }
        if (!context.contentTypeDefaults().isEmpty()) {
            entries.put("[Content_Types].xml", updateContentTypes(
                    entries.get("[Content_Types].xml"),
                    context.contentTypeDefaults()).getBytes(StandardCharsets.UTF_8));
        }
        for (var media : context.media()) {
            entries.put(media.path(), media.content());
        }

        try (var output = new ByteArrayOutputStream();
             var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    private boolean shouldBind(String entryName) {
        return entryName.startsWith("word/") && entryName.endsWith(".xml");
    }

    private String renderMainDocumentXml(String xml, DocxRenderContext context) throws IOException {
        var richReplaced = replaceRichPhotoSectionPlaceholders(xml, context);
        return replacePlaceholders(richReplaced, context.bindings());
    }

    private String replaceRichPhotoSectionPlaceholders(String xml, DocxRenderContext context) throws IOException {
        var sections = photoTableSections(context.request());
        var rendered = xml;
        for (var section : sections.entrySet()) {
            var placeholder = "${" + section.getKey() + "}";
            var tableXml = buildPhotoTableXml(section.getValue(), context);
            var matcher = WORD_PARAGRAPH_PATTERN.matcher(rendered);
            var result = new StringBuffer();
            var replaced = false;
            while (matcher.find()) {
                var paragraph = matcher.group();
                if (paragraph.contains(placeholder) || paragraphText(paragraph).contains(placeholder)) {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(tableXml));
                    replaced = true;
                }
            }
            matcher.appendTail(result);
            if (replaced) {
                rendered = result.toString();
            }
        }
        return rendered;
    }

    private Map<String, Map<String, Object>> photoTableSections(DocumentGenerationRequest request) {
        var layoutSections = mapValue(request.payload().get("layoutSections"));
        if (layoutSections.isEmpty()) {
            return Map.of();
        }
        var sections = new LinkedHashMap<String, Map<String, Object>>();
        layoutSections.forEach((key, rawSection) -> {
            var section = mapValue(rawSection);
            var type = normalizeCode(stringValue(section.get("type")));
            if ("PHOTO_TABLE".equals(type)) {
                sections.put(key, section);
            }
        });
        return sections;
    }

    private String paragraphText(String paragraphXml) {
        var matcher = WORD_TEXT_NODE_PATTERN.matcher(paragraphXml);
        var text = new StringBuilder();
        while (matcher.find()) {
            text.append(matcher.group(2));
        }
        return text.toString();
    }

    private String buildPhotoTableXml(Map<String, Object> section, DocxRenderContext context) throws IOException {
        var photos = context.request().photos() == null ? List.<PhotoAsset>of() : context.request().photos();
        var rows = new StringBuilder();
        var title = stringValue(section.get("title"));
        if (title != null && !title.isBlank()) {
            rows.append(tableRow(List.of(
                    tableCell(List.of(textParagraph(title)), "9000", 2))));
        }
        rows.append(tableRow(List.of(
                tableCell(List.of(textParagraph("Photo")), "4200", 1),
                tableCell(List.of(textParagraph("Description")), "4800", 1))));
        if (photos.isEmpty()) {
            rows.append(tableRow(List.of(
                    tableCell(List.of(textParagraph("No photos.")), "9000", 2))));
        } else {
            for (var photo : photos) {
                rows.append(tableRow(List.of(
                        tableCell(List.of(photoParagraph(photo, context)), "4200", 1),
                        tableCell(photoDescriptionParagraphs(photo), "4800", 1))));
            }
        }
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
                  <w:tblGrid><w:gridCol w:w="4200"/><w:gridCol w:w="4800"/></w:tblGrid>
                  %s
                </w:tbl>
                """.formatted(rows);
    }

    private String tableRow(List<String> cells) {
        return "<w:tr>" + String.join("", cells) + "</w:tr>";
    }

    private String tableCell(List<String> paragraphs, String width, int gridSpan) {
        var gridSpanXml = gridSpan > 1 ? "<w:gridSpan w:val=\"" + gridSpan + "\"/>" : "";
        return """
                <w:tc>
                  <w:tcPr><w:tcW w:w="%s" w:type="dxa"/>%s</w:tcPr>
                  %s
                </w:tc>
                """.formatted(width, gridSpanXml, String.join("", paragraphs));
    }

    private String photoParagraph(PhotoAsset photo, DocxRenderContext context) throws IOException {
        var image = resolvePhotoImage(photo, context);
        if (image.isEmpty()) {
            return textParagraph("Image unavailable");
        }
        return "<w:p><w:r>" + drawingXml(image.get()) + "</w:r></w:p>";
    }

    private List<String> photoDescriptionParagraphs(PhotoAsset photo) {
        var paragraphs = new ArrayList<String>();
        paragraphs.add(textParagraph("Photo ID: " + valueOrBlank(photo.photoId())));
        if (photo.checklistItemKey() != null && !photo.checklistItemKey().isBlank()) {
            paragraphs.add(textParagraph("Step: " + photo.checklistItemKey()));
        }
        if (photo.caption() != null && !photo.caption().isBlank()) {
            paragraphs.add(textParagraph("Caption: " + photo.caption()));
        }
        if (photo.storageRef() != null && !photo.storageRef().isBlank()) {
            paragraphs.add(textParagraph("Storage: " + photo.storageRef()));
        }
        return paragraphs;
    }

    private String textParagraph(String text) {
        return "<w:p><w:r><w:t>" + escapeXml(text) + "</w:t></w:r></w:p>";
    }

    private Optional<DocxImage> resolvePhotoImage(PhotoAsset photo, DocxRenderContext context) throws IOException {
        var content = photoContentResolver.resolve(photo);
        if (content.isEmpty() || content.get().content() == null || content.get().content().length == 0) {
            return Optional.empty();
        }
        return Optional.of(context.addImage(photo, content.get()));
    }

    private String drawingXml(DocxImage image) {
        return """
                <w:drawing>
                  <wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" distT="0" distB="0" distL="0" distR="0">
                    <wp:extent cx="%d" cy="%d"/>
                    <wp:docPr id="%d" name="ArchDox Photo %d"/>
                    <wp:cNvGraphicFramePr>
                      <a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/>
                    </wp:cNvGraphicFramePr>
                    <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                      <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                        <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                          <pic:nvPicPr>
                            <pic:cNvPr id="%d" name="%s"/>
                            <pic:cNvPicPr/>
                          </pic:nvPicPr>
                          <pic:blipFill>
                            <a:blip xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:embed="%s"/>
                            <a:stretch><a:fillRect/></a:stretch>
                          </pic:blipFill>
                          <pic:spPr>
                            <a:xfrm><a:off x="0" y="0"/><a:ext cx="%d" cy="%d"/></a:xfrm>
                            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                          </pic:spPr>
                        </pic:pic>
                      </a:graphicData>
                    </a:graphic>
                  </wp:inline>
                </w:drawing>
                """.formatted(
                image.widthEmu(),
                image.heightEmu(),
                image.docPrId(),
                image.docPrId(),
                image.docPrId(),
                escapeXml(image.fileName()),
                image.relationshipId(),
                image.widthEmu(),
                image.heightEmu());
    }

    private String updateDocumentRelationships(byte[] existing, List<DocxRelationship> relationships) {
        var xml = existing == null || existing.length == 0
                ? """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                </Relationships>
                """
                : new String(existing, StandardCharsets.UTF_8);
        if (relationships.isEmpty()) {
            return xml;
        }
        var additions = new StringBuilder();
        for (var relationship : relationships) {
            additions.append("""
                    <Relationship Id="%s" Type="%s" Target="%s"/>
                    """.formatted(
                    relationship.id(),
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image",
                    relationship.target()));
        }
        var close = xml.lastIndexOf("</Relationships>");
        if (close < 0) {
            return xml + additions;
        }
        return xml.substring(0, close) + additions + xml.substring(close);
    }

    private String updateContentTypes(byte[] existing, Map<String, String> contentTypeDefaults) {
        var xml = existing == null || existing.length == 0
                ? """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                </Types>
                """
                : new String(existing, StandardCharsets.UTF_8);
        var additions = new StringBuilder();
        for (var entry : contentTypeDefaults.entrySet()) {
            if (!hasDefaultContentType(xml, entry.getKey())) {
                additions.append("""
                        <Default Extension="%s" ContentType="%s"/>
                        """.formatted(entry.getKey(), entry.getValue()));
            }
        }
        if (additions.isEmpty()) {
            return xml;
        }
        var close = xml.lastIndexOf("</Types>");
        if (close < 0) {
            return xml + additions;
        }
        return xml.substring(0, close) + additions + xml.substring(close);
    }

    private boolean hasDefaultContentType(String xml, String extension) {
        var needle = "Extension=\"" + extension + "\"";
        return xml.contains(needle) || xml.contains("Extension='" + extension + "'");
    }

    private String replacePlaceholders(String xml, Map<String, String> bindings) {
        var replaced = replaceIntactPlaceholders(xml, bindings);
        return replaceSplitTextNodePlaceholders(replaced, bindings);
    }

    private String replaceIntactPlaceholders(String xml, Map<String, String> bindings) {
        var matcher = PLACEHOLDER_PATTERN.matcher(xml);
        var result = new StringBuffer();
        while (matcher.find()) {
            var replacement = bindings.get(matcher.group(1));
            if (replacement == null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(escapeXml(replacement)));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String replaceSplitTextNodePlaceholders(String xml, Map<String, String> bindings) {
        if (bindings.isEmpty() || !xml.contains("${")) {
            return xml;
        }

        var textNodes = extractWordTextNodes(xml);
        if (textNodes.size() < 2) {
            return xml;
        }

        var contents = new ArrayList<String>(textNodes.size());
        var textStarts = new int[textNodes.size()];
        var combined = new StringBuilder();
        for (int i = 0; i < textNodes.size(); i++) {
            var node = textNodes.get(i);
            textStarts[i] = combined.length();
            contents.add(node.content());
            combined.append(node.content());
        }

        var replacements = new ArrayList<SplitPlaceholderReplacement>();
        var matcher = PLACEHOLDER_PATTERN.matcher(combined);
        while (matcher.find()) {
            var replacement = bindings.get(matcher.group(1));
            if (replacement == null) {
                continue;
            }

            var start = locateTextPosition(contents, textStarts, matcher.start());
            var end = locateTextPosition(contents, textStarts, matcher.end() - 1);
            if (start.nodeIndex() == end.nodeIndex()) {
                continue;
            }

            replacements.add(new SplitPlaceholderReplacement(
                    start.nodeIndex(),
                    start.offset(),
                    end.nodeIndex(),
                    end.offset() + 1,
                    escapeXml(replacement)));
        }

        for (int i = replacements.size() - 1; i >= 0; i--) {
            applySplitReplacement(contents, replacements.get(i));
        }
        return rebuildXmlWithTextNodes(xml, textNodes, contents);
    }

    private List<WordTextNode> extractWordTextNodes(String xml) {
        var textNodes = new ArrayList<WordTextNode>();
        var matcher = WORD_TEXT_NODE_PATTERN.matcher(xml);
        while (matcher.find()) {
            textNodes.add(new WordTextNode(matcher.start(2), matcher.end(2), matcher.group(2)));
        }
        return textNodes;
    }

    private TextPosition locateTextPosition(List<String> contents, int[] textStarts, int charIndex) {
        for (int i = 0; i < contents.size(); i++) {
            var start = textStarts[i];
            var end = start + contents.get(i).length();
            if (charIndex >= start && charIndex < end) {
                return new TextPosition(i, charIndex - start);
            }
        }
        throw new IllegalStateException("Placeholder position is outside Word text nodes");
    }

    private void applySplitReplacement(List<String> contents, SplitPlaceholderReplacement replacement) {
        var first = contents.get(replacement.startNodeIndex());
        var last = contents.get(replacement.endNodeIndex());
        contents.set(
                replacement.startNodeIndex(),
                first.substring(0, replacement.startOffset()) + replacement.replacement());
        for (int i = replacement.startNodeIndex() + 1; i < replacement.endNodeIndex(); i++) {
            contents.set(i, "");
        }
        contents.set(replacement.endNodeIndex(), last.substring(replacement.endOffset()));
    }

    private String rebuildXmlWithTextNodes(String xml, List<WordTextNode> textNodes, List<String> contents) {
        var result = new StringBuilder(xml);
        for (int i = textNodes.size() - 1; i >= 0; i--) {
            var node = textNodes.get(i);
            result.replace(node.contentStart(), node.contentEnd(), contents.get(i));
        }
        return result.toString();
    }

    private Map<String, String> buildBindings(DocumentGenerationRequest request) {
        var bindings = new LinkedHashMap<String, String>();
        bindings.put("jobId", valueOrBlank(request.jobId()));
        bindings.put("officeCode", valueOrBlank(request.officeCode()));
        bindings.put("reportId", valueOrBlank(request.reportId()));
        bindings.put("templateCode", valueOrBlank(request.template().templateCode()));
        bindings.put("templateVersion", String.valueOf(request.template().version()));
        bindings.put("generatedAt", OffsetDateTime.now().toString());

        flatten(bindings, "", request.payload());
        addTemplateFieldAliases(bindings);
        addLeafAliases(bindings);
        return bindings;
    }

    private void flatten(Map<String, String> bindings, String prefix, Object value) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> {
                var childKey = prefix.isBlank() ? String.valueOf(key) : prefix + "." + key;
                flatten(bindings, childKey, nested);
            });
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                flatten(bindings, prefix + "[" + i + "]", list.get(i));
            }
            return;
        }
        if (!prefix.isBlank()) {
            bindings.put(prefix, valueOrBlank(value));
        }
    }

    private void addTemplateFieldAliases(Map<String, String> bindings) {
        var prefix = "templateFields.";
        var aliases = new LinkedHashMap<String, String>();
        for (var entry : bindings.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            var alias = entry.getKey().substring(prefix.length());
            if (!alias.isBlank() && !alias.contains(".") && !alias.contains("[")) {
                aliases.put(alias, entry.getValue());
            }
        }
        bindings.putAll(aliases);
    }

    private void addLeafAliases(Map<String, String> bindings) {
        var leafCounts = new LinkedHashMap<String, Integer>();
        var leafValues = new LinkedHashMap<String, String>();
        for (var entry : bindings.entrySet()) {
            var leaf = leafName(entry.getKey());
            if (leaf == null || leaf.isBlank()) {
                continue;
            }
            leafCounts.put(leaf, leafCounts.getOrDefault(leaf, 0) + 1);
            leafValues.putIfAbsent(leaf, entry.getValue());
        }
        leafCounts.forEach((leaf, count) -> {
            if (count == 1) {
                bindings.putIfAbsent(leaf, leafValues.get(leaf));
            }
        });
    }

    private String leafName(String key) {
        var dot = key.lastIndexOf('.');
        var bracket = key.lastIndexOf(']');
        var index = Math.max(dot, bracket);
        if (index < 0 || index + 1 >= key.length()) {
            return key;
        }
        return key.substring(index + 1);
    }

    private String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
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

    private String stringValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value);
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        raw.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private String imageExtension(ResolvedPhotoContent content, PhotoAsset photo) {
        var mimeType = content.mimeType() == null ? "" : content.mimeType().toLowerCase(Locale.ROOT);
        return switch (mimeType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/jpg", "image/jpeg" -> "jpeg";
            default -> extensionFromStorageRef(photo.storageRef()).orElse("jpeg");
        };
    }

    private Optional<String> extensionFromStorageRef(String storageRef) {
        if (storageRef == null || storageRef.isBlank() || !storageRef.contains(".")) {
            return Optional.empty();
        }
        var extension = storageRef.substring(storageRef.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "jpg" -> Optional.of("jpeg");
            case "jpeg", "png", "gif", "webp" -> Optional.of(extension);
            default -> Optional.empty();
        };
    }

    private String imageContentType(String extension, ResolvedPhotoContent content) {
        var mimeType = content.mimeType() == null ? "" : content.mimeType().toLowerCase(Locale.ROOT);
        if (!mimeType.isBlank()) {
            return "image/jpg".equals(mimeType) ? "image/jpeg" : mimeType;
        }
        return switch (extension) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private long[] imageSize(PhotoLayoutSize layoutSize) {
        var size = layoutSize == null ? PhotoLayoutSize.MEDIUM : layoutSize;
        return switch (size) {
            case THUMBNAIL -> new long[]{1_371_600L, 1_028_700L};
            case ORIGINAL -> new long[]{4_572_000L, 3_429_000L};
            case MEDIUM -> new long[]{2_743_200L, 2_057_400L};
        };
    }

    private final class DocxRenderContext {
        private final DocumentGenerationRequest request;
        private final Map<String, String> bindings;
        private final List<DocxMedia> media = new ArrayList<>();
        private final List<DocxRelationship> relationships = new ArrayList<>();
        private final Map<String, String> contentTypeDefaults = new LinkedHashMap<>();
        private final Set<String> mediaPaths = new HashSet<>();
        private int imageCounter;

        private DocxRenderContext(DocumentGenerationRequest request, Map<String, String> bindings) {
            this.request = request;
            this.bindings = bindings;
        }

        private DocumentGenerationRequest request() {
            return request;
        }

        private Map<String, String> bindings() {
            return bindings;
        }

        private List<DocxMedia> media() {
            return media;
        }

        private List<DocxRelationship> relationships() {
            return relationships;
        }

        private Map<String, String> contentTypeDefaults() {
            return contentTypeDefaults;
        }

        private DocxImage addImage(PhotoAsset photo, ResolvedPhotoContent content) {
            imageCounter++;
            var extension = imageExtension(content, photo);
            var fileName = "archdox-photo-" + imageCounter + "." + extension;
            var path = "word/media/" + fileName;
            while (mediaPaths.contains(path)) {
                imageCounter++;
                fileName = "archdox-photo-" + imageCounter + "." + extension;
                path = "word/media/" + fileName;
            }
            mediaPaths.add(path);
            var relationshipId = "rIdArchDoxImage" + imageCounter;
            relationships.add(new DocxRelationship(relationshipId, "media/" + fileName));
            contentTypeDefaults.putIfAbsent(extension, imageContentType(extension, content));
            media.add(new DocxMedia(path, content.content()));
            var size = imageSize(photo.layoutSize());
            return new DocxImage(relationshipId, fileName, imageCounter, size[0], size[1]);
        }
    }

    private record WordTextNode(int contentStart, int contentEnd, String content) {
    }

    private record TextPosition(int nodeIndex, int offset) {
    }

    private record SplitPlaceholderReplacement(
            int startNodeIndex,
            int startOffset,
            int endNodeIndex,
            int endOffset,
            String replacement) {
    }

    private record DocxMedia(String path, byte[] content) {
    }

    private record DocxRelationship(String id, String target) {
    }

    private record DocxImage(
            String relationshipId,
            String fileName,
            int docPrId,
            long widthEmu,
            long heightEmu
    ) {
    }
}

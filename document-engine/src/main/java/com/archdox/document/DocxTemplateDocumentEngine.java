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
    private static final int DEFAULT_TABLE_WIDTH = 9000;
    private static final String DEFAULT_BORDER_COLOR = "D9DDE3";
    private static final String DEFAULT_HEADER_FILL = "F3F4F6";
    private static final String DEFAULT_TITLE_FILL = "EEF2F7";

    private final TemplateContentResolver templateContentResolver;
    private final PhotoContentResolver photoContentResolver;
    private final DocumentArtifactExportService exportService;
    private final HtmlPreviewDocumentRenderer htmlRenderer;
    private final DocumentEngine fallback;

    public DocxTemplateDocumentEngine(TemplateContentResolver templateContentResolver, DocumentEngine fallback) {
        this(templateContentResolver, photo -> Optional.empty(), fallback);
    }

    public DocxTemplateDocumentEngine(
            TemplateContentResolver templateContentResolver,
            DocumentEngine fallback,
            DocumentArtifactExportService exportService
    ) {
        this(templateContentResolver, photo -> Optional.empty(), fallback, exportService);
    }

    public DocxTemplateDocumentEngine(
            TemplateContentResolver templateContentResolver,
            PhotoContentResolver photoContentResolver,
            DocumentEngine fallback
    ) {
        this(templateContentResolver, photoContentResolver, fallback, DocumentArtifactExportService.disabled());
    }

    public DocxTemplateDocumentEngine(
            TemplateContentResolver templateContentResolver,
            PhotoContentResolver photoContentResolver,
            DocumentEngine fallback,
            DocumentArtifactExportService exportService
    ) {
        this.templateContentResolver = templateContentResolver;
        this.photoContentResolver = photoContentResolver;
        this.exportService = exportService == null ? DocumentArtifactExportService.disabled() : exportService;
        this.htmlRenderer = new HtmlPreviewDocumentRenderer(this.photoContentResolver);
        this.fallback = fallback;
    }

    @Override
    public DocumentGenerationResult generate(DocumentGenerationRequest request) {
        try {
            Optional<byte[]> templateContent;
            try {
                templateContent = templateContentResolver.resolve(request.template());
            } catch (IOException ex) {
                if (request.template().contentRequired()) {
                    return DocumentGenerationResult.failed(
                            request.jobId(),
                            "DOCUMENT_TEMPLATE_CONTENT_UNAVAILABLE",
                            "Document template content could not be read: " + ex.getMessage());
                }
                throw ex;
            }
            if (templateContent.isEmpty()) {
                if (request.template().contentRequired()) {
                    return DocumentGenerationResult.failed(
                            request.jobId(),
                            "DOCUMENT_TEMPLATE_CONTENT_NOT_FOUND",
                            "Document template content was not found for "
                                    + request.template().templateCode()
                                    + " v"
                                    + request.template().version());
                }
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
            return DocumentGenerationArtifacts.completeFromDocx(request, artifact, exportService, htmlRenderer);
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
        var richReplaced = replaceRichSectionPlaceholders(xml, context);
        return replacePlaceholders(richReplaced, context.bindings());
    }

    private String replaceRichSectionPlaceholders(String xml, DocxRenderContext context) throws IOException {
        var sections = richLayoutSections(context.request());
        var rendered = xml;
        for (var section : sections.entrySet()) {
            var placeholder = "${" + section.getKey() + "}";
            var replacementXml = buildRichSectionXml(section.getValue(), context);
            if (replacementXml == null || replacementXml.isBlank()) {
                continue;
            }
            var matcher = WORD_PARAGRAPH_PATTERN.matcher(rendered);
            var result = new StringBuffer();
            var replaced = false;
            while (matcher.find()) {
                var paragraph = matcher.group();
                if (paragraph.contains(placeholder) || paragraphText(paragraph).contains(placeholder)) {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(replacementXml));
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

    private String buildRichSectionXml(Map<String, Object> section, DocxRenderContext context) throws IOException {
        return switch (normalizeCode(stringValue(section.get("type")))) {
            case "PHOTO_TABLE" -> buildPhotoTableXml(section, context);
            case "CHECKLIST_TABLE" -> buildChecklistTableXml(section, context);
            default -> null;
        };
    }

    private Map<String, Map<String, Object>> richLayoutSections(DocumentGenerationRequest request) {
        var layoutSections = mapValue(request.payload().get("layoutSections"));
        if (layoutSections.isEmpty()) {
            return Map.of();
        }
        var sections = new LinkedHashMap<String, Map<String, Object>>();
        layoutSections.forEach((key, rawSection) -> {
            var section = mapValue(rawSection);
            var type = normalizeCode(stringValue(section.get("type")));
            if ("PHOTO_TABLE".equals(type) || "CHECKLIST_TABLE".equals(type)) {
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
        var tableOptions = tableOptions(section);
        var photosPerRow = photosPerRow(section);
        var imageSize = sectionImageSize(section);
        if (photosPerRow > 1) {
            return buildPhotoGridTableXml(section, photos, photosPerRow, imageSize, tableOptions, context);
        }

        var columnWidths = photoDetailColumnWidths(section, tableOptions.tableWidth());
        var rows = new StringBuilder();
        var title = stringValue(section.get("title"));
        if (includeTitle(section) && title != null && !title.isBlank()) {
            rows.append(tableRow(List.of(
                    tableCell(List.of(textParagraph(title, true)), String.valueOf(tableOptions.tableWidth()), 2, tableOptions.titleFill()))));
        }
        rows.append(tableRow(List.of(
                tableCell(List.of(textParagraph("Photo", true)), columnWidths.get(0), 1, tableOptions.headerFill()),
                tableCell(List.of(textParagraph("Description", true)), columnWidths.get(1), 1, tableOptions.headerFill()))));
        if (photos.isEmpty()) {
            rows.append(tableRow(List.of(
                    tableCell(
                            List.of(textParagraph(emptyText(section, "No photos."))),
                            String.valueOf(tableOptions.tableWidth()),
                            2))));
        } else {
            for (var photo : photos) {
                rows.append(tableRow(List.of(
                        tableCell(List.of(photoParagraph(photo, imageSize, context)), columnWidths.get(0), 1),
                        tableCell(
                                photoDescriptionParagraphs(photo, sectionFields(section, defaultPhotoDescriptionFields())),
                                columnWidths.get(1),
                                1))));
            }
        }
        return tableXml(columnWidths, rows.toString(), tableOptions);
    }

    private String buildChecklistTableXml(Map<String, Object> section, DocxRenderContext context) {
        var checklistAnswers = listValue(context.request().payload().get("checklistAnswers"));
        var tableOptions = tableOptions(section);
        var fields = sectionFields(section, defaultChecklistTableFields());
        var columnWidths = checklistColumnWidths(section, fields, tableOptions.tableWidth());
        var rows = new StringBuilder();
        var title = stringValue(section.get("title"));
        if (includeTitle(section) && title != null && !title.isBlank()) {
            rows.append(tableRow(List.of(
                    tableCell(List.of(textParagraph(title, true)), String.valueOf(tableOptions.tableWidth()), fields.size(), tableOptions.titleFill()))));
        }
        rows.append(tableRow(checklistHeaderCells(fields, columnWidths, tableOptions)));
        if (checklistAnswers.isEmpty()) {
            rows.append(tableRow(List.of(
                    tableCell(
                            List.of(textParagraph(emptyText(section, "No checklist answers."))),
                            String.valueOf(tableOptions.tableWidth()),
                            fields.size()))));
        } else {
            for (var answer : checklistAnswers) {
                rows.append(tableRow(checklistValueCells(answer, fields, columnWidths)));
            }
        }
        return tableXml(columnWidths, rows.toString(), tableOptions);
    }

    private List<String> checklistHeaderCells(
            List<Map<String, String>> fields,
            List<String> columnWidths,
            TableOptions tableOptions
    ) {
        var cells = new ArrayList<String>();
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            var label = field.get("label");
            if (label == null || label.isBlank()) {
                label = field.getOrDefault("source", "");
            }
            cells.add(tableCell(List.of(textParagraph(label, true)), columnWidths.get(i), 1, tableOptions.headerFill()));
        }
        return cells;
    }

    private List<String> checklistValueCells(
            Object answer,
            List<Map<String, String>> fields,
            List<String> columnWidths
    ) {
        var cells = new ArrayList<String>();
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            cells.add(tableCell(
                    List.of(textParagraph(checklistFieldValue(answer, field.get("source")))),
                    columnWidths.get(i),
                    1));
        }
        return cells;
    }

    private String buildPhotoGridTableXml(
            Map<String, Object> section,
            List<PhotoAsset> photos,
            int photosPerRow,
            PhotoLayoutSize imageSize,
            TableOptions tableOptions,
            DocxRenderContext context
    ) throws IOException {
        var rows = new StringBuilder();
        var title = stringValue(section.get("title"));
        var cellWidth = String.valueOf(tableOptions.tableWidth() / photosPerRow);
        if (includeTitle(section) && title != null && !title.isBlank()) {
            rows.append(tableRow(List.of(
                    tableCell(List.of(textParagraph(title, true)), String.valueOf(tableOptions.tableWidth()), photosPerRow, tableOptions.titleFill()))));
        }
        if (photos.isEmpty()) {
            rows.append(tableRow(List.of(
                    tableCell(
                            List.of(textParagraph(emptyText(section, "No photos."))),
                            String.valueOf(tableOptions.tableWidth()),
                            photosPerRow))));
        } else {
            var fields = sectionFields(section, defaultPhotoDescriptionFields());
            for (int i = 0; i < photos.size(); i += photosPerRow) {
                var cells = new ArrayList<String>();
                for (int column = 0; column < photosPerRow; column++) {
                    var index = i + column;
                    if (index >= photos.size()) {
                        cells.add(tableCell(List.of(textParagraph("")), cellWidth, 1));
                        continue;
                    }
                    var photo = photos.get(index);
                    var paragraphs = new ArrayList<String>();
                    paragraphs.add(photoParagraph(photo, imageSize, context));
                    paragraphs.addAll(photoDescriptionParagraphs(photo, fields));
                    cells.add(tableCell(paragraphs, cellWidth, 1));
                }
                rows.append(tableRow(cells));
            }
        }
        var widths = new ArrayList<String>();
        for (int i = 0; i < photosPerRow; i++) {
            widths.add(cellWidth);
        }
        return tableXml(widths, rows.toString(), tableOptions);
    }

    private String tableXml(List<String> columnWidths, String rows, TableOptions tableOptions) {
        var tableStyleXml = tableOptions.tableStyle() == null || tableOptions.tableStyle().isBlank()
                ? ""
                : "<w:tblStyle w:val=\"" + escapeXml(tableOptions.tableStyle()) + "\"/>";
        return """
                <w:tbl>
                  <w:tblPr>
                    %s
                    <w:tblW w:w="%d" w:type="dxa"/>
                    <w:tblBorders>
                      <w:top w:val="single" w:sz="4" w:space="0" w:color="%s"/>
                      <w:left w:val="single" w:sz="4" w:space="0" w:color="%s"/>
                      <w:bottom w:val="single" w:sz="4" w:space="0" w:color="%s"/>
                      <w:right w:val="single" w:sz="4" w:space="0" w:color="%s"/>
                      <w:insideH w:val="single" w:sz="4" w:space="0" w:color="%s"/>
                      <w:insideV w:val="single" w:sz="4" w:space="0" w:color="%s"/>
                    </w:tblBorders>
                  </w:tblPr>
                  <w:tblGrid>%s</w:tblGrid>
                  %s
                </w:tbl>
                """.formatted(
                tableStyleXml,
                tableOptions.tableWidth(),
                tableOptions.borderColor(),
                tableOptions.borderColor(),
                tableOptions.borderColor(),
                tableOptions.borderColor(),
                tableOptions.borderColor(),
                tableOptions.borderColor(),
                tableGrid(columnWidths),
                rows);
    }

    private String tableGrid(List<String> columnWidths) {
        return columnWidths.stream()
                .map(width -> "<w:gridCol w:w=\"" + width + "\"/>")
                .reduce("", String::concat);
    }

    private List<String> equalColumnWidths(int columnCount) {
        return equalColumnWidths(columnCount, DEFAULT_TABLE_WIDTH);
    }

    private List<String> equalColumnWidths(int columnCount, int tableWidth) {
        var count = Math.max(1, columnCount);
        var widths = new ArrayList<String>();
        var baseWidth = tableWidth / count;
        var usedWidth = 0;
        for (int i = 0; i < count; i++) {
            var width = i == count - 1 ? tableWidth - usedWidth : baseWidth;
            widths.add(String.valueOf(width));
            usedWidth += width;
        }
        return widths;
    }

    private String tableRow(List<String> cells) {
        return "<w:tr>" + String.join("", cells) + "</w:tr>";
    }

    private String tableCell(List<String> paragraphs, String width, int gridSpan) {
        return tableCell(paragraphs, width, gridSpan, null);
    }

    private String tableCell(List<String> paragraphs, String width, int gridSpan, String fill) {
        var gridSpanXml = gridSpan > 1 ? "<w:gridSpan w:val=\"" + gridSpan + "\"/>" : "";
        var fillXml = fill == null || fill.isBlank() ? "" : "<w:shd w:fill=\"" + fill + "\"/>";
        return """
                <w:tc>
                  <w:tcPr><w:tcW w:w="%s" w:type="dxa"/>%s%s</w:tcPr>
                  %s
                </w:tc>
                """.formatted(width, gridSpanXml, fillXml, String.join("", paragraphs));
    }

    private String photoParagraph(PhotoAsset photo, PhotoLayoutSize imageSize, DocxRenderContext context) throws IOException {
        var image = resolvePhotoImage(photo, imageSize, context);
        if (image.isEmpty()) {
            return textParagraph("Image unavailable");
        }
        return "<w:p><w:r>" + drawingXml(image.get()) + "</w:r></w:p>";
    }

    private List<String> photoDescriptionParagraphs(PhotoAsset photo, List<Map<String, String>> fields) {
        var paragraphs = new ArrayList<String>();
        for (var field : fields) {
            var value = photoFieldValue(photo, field.get("source"));
            if (value == null || value.isBlank()) {
                continue;
            }
            var label = field.get("label");
            paragraphs.add(textParagraph(label == null || label.isBlank() ? value : label + ": " + value));
        }
        return paragraphs;
    }

    private String textParagraph(String text) {
        return textParagraph(text, false);
    }

    private String textParagraph(String text, boolean bold) {
        var runProperties = bold ? "<w:rPr><w:b/></w:rPr>" : "";
        return "<w:p><w:r>" + runProperties + "<w:t>" + escapeXml(text) + "</w:t></w:r></w:p>";
    }

    private int photosPerRow(Map<String, Object> section) {
        return Math.max(1, Math.min(3, intValue(section.get("photosPerRow"), 1)));
    }

    private PhotoLayoutSize sectionImageSize(Map<String, Object> section) {
        var value = stringValue(section.get("imageSize"));
        if (value == null) {
            value = stringValue(section.get("layoutSize"));
        }
        if (value == null) {
            value = stringValue(section.get("photoLayoutSize"));
        }
        if (value == null) {
            return PhotoLayoutSize.MEDIUM;
        }
        try {
            return PhotoLayoutSize.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PhotoLayoutSize.MEDIUM;
        }
    }

    private TableOptions tableOptions(Map<String, Object> section) {
        return new TableOptions(
                Math.max(1000, intValue(section.get("tableWidth"), DEFAULT_TABLE_WIDTH)),
                stringValue(section.get("tableStyle")),
                colorValue(section.get("borderColor"), DEFAULT_BORDER_COLOR),
                colorValue(section.get("headerFill"), DEFAULT_HEADER_FILL),
                colorValue(section.get("titleFill"), DEFAULT_TITLE_FILL));
    }

    private boolean includeTitle(Map<String, Object> section) {
        return !Boolean.FALSE.equals(section.get("includeTitle"));
    }

    private String emptyText(Map<String, Object> section, String fallback) {
        var value = firstString(section, "emptyText", "emptyMessage");
        return value == null ? fallback : value;
    }

    private List<String> photoDetailColumnWidths(Map<String, Object> section, int tableWidth) {
        var configured = configuredColumnWidths(section.get("columnWidths"), 2);
        if (!configured.isEmpty()) {
            return configured;
        }
        var photoWidth = intValue(section.get("photoColumnWidth"), Math.min(4200, tableWidth));
        var descriptionWidth = intValue(section.get("descriptionColumnWidth"), tableWidth - photoWidth);
        if (photoWidth <= 0 || descriptionWidth <= 0) {
            return List.of("4200", "4800");
        }
        return List.of(String.valueOf(photoWidth), String.valueOf(descriptionWidth));
    }

    private List<String> checklistColumnWidths(
            Map<String, Object> section,
            List<Map<String, String>> fields,
            int tableWidth
    ) {
        var configured = configuredColumnWidths(section.get("columnWidths"), fields.size());
        if (!configured.isEmpty()) {
            return configured;
        }
        var fieldWidths = widthsFromFields(fields);
        if (!fieldWidths.isEmpty()) {
            return fieldWidths;
        }
        return equalColumnWidths(fields.size(), tableWidth);
    }

    private List<String> configuredColumnWidths(Object value, int expectedCount) {
        if (!(value instanceof List<?> rawWidths) || rawWidths.size() != expectedCount) {
            return List.of();
        }
        var widths = new ArrayList<String>();
        for (var rawWidth : rawWidths) {
            var width = positiveWidth(rawWidth);
            if (width == null) {
                return List.of();
            }
            widths.add(width);
        }
        return widths;
    }

    private List<String> widthsFromFields(List<Map<String, String>> fields) {
        var widths = new ArrayList<String>();
        for (var field : fields) {
            var width = positiveWidth(field.get("width"));
            if (width == null) {
                return List.of();
            }
            widths.add(width);
        }
        return widths;
    }

    private List<Map<String, String>> sectionFields(
            Map<String, Object> section,
            List<Map<String, String>> defaultFields
    ) {
        var value = section.get("fields");
        if (!(value instanceof List<?> fields) || fields.isEmpty()) {
            return defaultFields;
        }
        var parsed = new ArrayList<Map<String, String>>();
        for (var field : fields) {
            if (field instanceof String source && !source.isBlank()) {
                parsed.add(Map.of("source", source));
                continue;
            }
            if (field instanceof Map<?, ?> map) {
                var source = firstString(map, "source", "path", "key");
                if (source == null) {
                    continue;
                }
                var label = firstString(map, "label", "title");
                var width = firstString(map, "width", "columnWidth");
                var parsedField = new LinkedHashMap<String, String>();
                parsedField.put("source", source);
                if (label != null) {
                    parsedField.put("label", label);
                }
                if (width != null) {
                    parsedField.put("width", width);
                }
                parsed.add(parsedField);
            }
        }
        return parsed.isEmpty() ? defaultFields : parsed;
    }

    private List<Map<String, String>> defaultPhotoDescriptionFields() {
        return List.of(
                Map.of("label", "Photo ID", "source", "photoId"),
                Map.of("label", "Step", "source", "stepCode"),
                Map.of("label", "Caption", "source", "caption"),
                Map.of("label", "Storage", "source", "storageRef"));
    }

    private List<Map<String, String>> defaultChecklistTableFields() {
        return List.of(
                Map.of("label", "Item", "source", "itemCode"),
                Map.of("label", "Label", "source", "label"),
                Map.of("label", "Answer", "source", "answer.value"),
                Map.of("label", "Note", "source", "note"));
    }

    private String photoFieldValue(PhotoAsset photo, String source) {
        return switch (normalizeCode(source)) {
            case "PHOTOID", "PHOTO_ID", "ID" -> valueOrBlank(photo.photoId());
            case "CHECKLISTITEMKEY", "CHECKLIST_ITEM_KEY", "STEPCODE", "STEP_CODE", "STEP" ->
                    valueOrBlank(photo.checklistItemKey());
            case "CAPTION", "DESCRIPTION", "DESC" -> valueOrBlank(photo.caption());
            case "STORAGEREF", "STORAGE_REF", "WORKINGSTORAGEREF", "WORKING_STORAGE_REF" ->
                    valueOrBlank(photo.storageRef());
            case "MIMETYPE", "MIME_TYPE" -> valueOrBlank(photo.mimeType());
            default -> "";
        };
    }

    private String checklistFieldValue(Object answer, String source) {
        return readPath(answer, source)
                .map(this::textValue)
                .orElse("");
    }

    private String textValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> map) {
            var direct = firstString(map, "value", "result", "label", "name");
            if (direct != null) {
                return direct;
            }
        }
        return String.valueOf(value);
    }

    private String firstString(Map<?, ?> map, String... keys) {
        for (var key : keys) {
            var value = map.get(key);
            var text = stringValue(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private Optional<DocxImage> resolvePhotoImage(
            PhotoAsset photo,
            PhotoLayoutSize imageSize,
            DocxRenderContext context
    ) throws IOException {
        var content = photoContentResolver.resolve(photo);
        if (content.isEmpty() || content.get().content() == null || content.get().content().length == 0) {
            return Optional.empty();
        }
        return Optional.of(context.addImage(photo, content.get(), imageSize));
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

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String positiveWidth(Object value) {
        var width = intValue(value, -1);
        return width > 0 ? String.valueOf(width) : null;
    }

    private String colorValue(Object value, String fallback) {
        var text = stringValue(value);
        if (text == null) {
            return fallback;
        }
        var normalized = text.trim().replace("#", "").toUpperCase(Locale.ROOT);
        return normalized.matches("[0-9A-F]{6}") ? normalized : fallback;
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        raw.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private Optional<Object> readPath(Object root, String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        Object current = root;
        for (String segment : path.split("\\.")) {
            current = readSegment(current, segment);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    private Object readSegment(Object current, String segment) {
        if (segment == null || segment.isBlank()) {
            return null;
        }
        var key = segment;
        Integer index = null;
        var bracketStart = segment.indexOf('[');
        if (bracketStart >= 0 && segment.endsWith("]")) {
            key = segment.substring(0, bracketStart);
            var rawIndex = segment.substring(bracketStart + 1, segment.length() - 1);
            try {
                index = Integer.parseInt(rawIndex);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        Object value = current;
        if (!key.isBlank()) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            value = map.get(key);
        }
        if (index == null) {
            return value;
        }
        if (value instanceof List<?> list && index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return null;
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

        private DocxImage addImage(PhotoAsset photo, ResolvedPhotoContent content, PhotoLayoutSize imageSize) {
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
            var size = imageSize(imageSize == null ? photo.layoutSize() : imageSize);
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

    private record TableOptions(
            int tableWidth,
            String tableStyle,
            String borderColor,
            String headerFill,
            String titleFill
    ) {
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

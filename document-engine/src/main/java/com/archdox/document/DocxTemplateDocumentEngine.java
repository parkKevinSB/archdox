package com.archdox.document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
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
    private static final int OFFICIAL_PAGE_WIDTH = 10460;
    private static final long SIGNATURE_WIDTH_EMU = 1_120_000L;
    private static final long SIGNATURE_HEIGHT_EMU = 420_000L;

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
        if (shouldRenderOfficialConstructionDailyLog(context.request())) {
            return renderOfficialConstructionDailyLogXml(context);
        }
        var richReplaced = replaceRichSectionPlaceholders(xml, context);
        var signatureReplaced = applySignatureBlock(richReplaced, context);
        return replacePlaceholders(signatureReplaced, context.bindings());
    }

    private boolean shouldRenderOfficialConstructionDailyLog(DocumentGenerationRequest request) {
        var templateCode = normalizeCode(request.template().templateCode());
        var storageRef = normalizeStorageRef(request.template().storageRef());
        if (isBundledConstructionDailyLogStorageRef(storageRef)) {
            return true;
        }
        if (storageRef.isBlank()
                && templateCode.contains("KOREAN_CONSTRUCTION_DAILY_SUPERVISION")
                && templateCode.contains("APPENDIX_2")) {
            return true;
        }
        var report = mapValue(request.payload().get("report"));
        var documentType = mapValue(request.payload().get("documentType"));
        var reportType = firstNonBlank(
                stringValue(report.get("reportType")),
                stringValue(documentType.get("reportType")),
                stringValue(documentType.get("code")));
        return isConstructionDailyLogType(reportType)
                && isBundledConstructionDailyLogStorageRef(storageRef);
    }

    private String normalizeStorageRef(String storageRef) {
        var normalized = valueOrBlank(storageRef).trim().replace('\\', '/').toUpperCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private boolean isBundledConstructionDailyLogStorageRef(String storageRef) {
        return storageRef.equals("TEMPLATES/KOREAN/KOREAN-CONSTRUCTION-DAILY-SUPERVISION-LOG-APPENDIX-2.DOCX")
                || storageRef.endsWith("/TEMPLATES/KOREAN/KOREAN-CONSTRUCTION-DAILY-SUPERVISION-LOG-APPENDIX-2.DOCX");
    }

    private boolean isConstructionDailyLogType(String value) {
        var code = normalizeCode(value);
        return "DAILY_SUPERVISION".equals(code)
                || "CONSTRUCTION_DAILY_LOG".equals(code)
                || "CONSTRUCTION_DAILY_SUPERVISION_LOG".equals(code);
    }

    private String renderOfficialConstructionDailyLogXml(DocxRenderContext context) throws IOException {
        var body = new StringBuilder();
        body.append(officialDailyLogTitleXml());
        body.append(officialDailyLogHeaderXml(context));
        body.append(spacerParagraph(80));
        body.append(officialDailyLogWorkTableXml(context));
        body.append(spacerParagraph(70));
        body.append(officialLinedSectionXml("특기사항", binding(context, "specialNotes"), 1420));
        body.append(spacerParagraph(70));
        body.append(officialLinedSectionXml(
                "지적사항 및 처리결과",
                binding(context, "issueAndAction", "correctionResults"),
                1600));
        var nextAction = binding(context, "nextAction");
        if (!nextAction.isBlank()) {
            body.append(spacerParagraph(70));
            body.append(officialLinedSectionXml("다음 조치", nextAction, 1000));
        }
        body.append(officialPhotoEvidenceXml(context));
        body.append(spacerParagraph(100));
        body.append(officialAuthoringGuideXml());
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <w:body>
                    %s
                    <w:sectPr>
                      <w:pgSz w:w="11906" w:h="16838"/>
                      <w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720" w:header="360" w:footer="360" w:gutter="0"/>
                    </w:sectPr>
                  </w:body>
                </w:document>
                """.formatted(body);
    }

    private String officialDailyLogTitleXml() {
        return """
                %s
                <w:tbl>
                  <w:tblPr>
                    <w:tblW w:w="%d" w:type="dxa"/>
                    <w:tblBorders>
                      <w:top w:val="nil"/>
                      <w:left w:val="nil"/>
                      <w:bottom w:val="nil"/>
                      <w:right w:val="nil"/>
                      <w:insideH w:val="nil"/>
                      <w:insideV w:val="nil"/>
                    </w:tblBorders>
                  </w:tblPr>
                  <w:tblGrid><w:gridCol w:w="3138"/><w:gridCol w:w="4184"/><w:gridCol w:w="3138"/></w:tblGrid>
                  %s
                </w:tbl>
                %s
                """.formatted(
                officialParagraph("■ 건축공사 감리세부기준 [별지 제2호서식]  <개정 2017. 2. 4.>", false, 15, "left", "0000FF"),
                OFFICIAL_PAGE_WIDTH,
                officialRow(List.of(
                        officialCell(List.of(officialParagraph("", false, 20, "left")), "3138", 1),
                        officialCell(List.of(officialParagraph("공사감리일지", true, 44, "center")), "4184", 1),
                        officialCell(List.of(officialParagraph("", false, 20, "right")), "3138", 1)),
                        780),
                horizontalRuleParagraph(14));
    }

    private String officialDailyLogHeaderXml(DocxRenderContext context) throws IOException {
        var date = inspectionDateParts(context);
        var serialNo = binding(context, "serialNo", "reportNo");
        var constructionName = binding(context, "constructionName", "constructionProjectName", "projectName");
        var dateLine = "공사    "
                + blankIfMissing(date.year()) + " 년    "
                + blankIfMissing(date.month()) + " 월    "
                + blankIfMissing(date.day()) + " 일(    "
                + blankIfMissing(date.dayOfWeek()) + " 요일)    날씨 : "
                + binding(context, "weather");
        var supervisorCell = new ArrayList<String>();
        supervisorCell.add(officialParagraph("총괄감리책임자    " + binding(context, "chiefSupervisorName", "supervisorName", "inspectorName"), false, 20, "left"));
        supervisorCell.add(officialSignatureMarkParagraph(context, "CHIEF_SUPERVISOR", true));
        var assistantCell = List.of(
                officialParagraph("건축사보    " + binding(context, "architectAssistantName", "assistantArchitectName", "assistantSupervisorName"), false, 20, "left"),
                officialSignatureMarkParagraph(context, "ARCHITECT_ASSISTANT", false));
        var rows = new StringBuilder();
        rows.append(officialRow(List.of(
                officialCell(List.of(officialParagraph("일련번호    " + serialNo, false, 18, "left")), String.valueOf(OFFICIAL_PAGE_WIDTH), 4)),
                500));
        rows.append(officialRow(List.of(
                officialCell(supervisorCell, "5230", 2),
                officialCell(assistantCell, "5230", 2)),
                650));
        rows.append(officialRow(List.of(
                officialCell(List.of(officialParagraph("공사명", false, 22, "left")), "1700", 1),
                officialCell(List.of(
                        officialParagraph(constructionName, false, 21, "left"),
                        officialParagraph(dateLine, false, 21, "center")), "8760", 3)),
                900));
        return officialTableXml(
                List.of("1700", "3530", "1800", "3430"),
                rows.toString(),
                OFFICIAL_PAGE_WIDTH,
                0,
                0,
                14,
                0,
                4,
                4);
    }

    private String officialDailyLogWorkTableXml(DocxRenderContext context) {
        var rows = new StringBuilder();
        var items = officialSupervisionRows(context);
        rows.append(officialRow(List.of(
                officialCell(officialParagraphs("공종 및 세부공정\n(     층)", 18, "center"), "3100", 1),
                officialCell(List.of(officialParagraph("감리 항목", false, 18, "center")), "2800", 1),
                officialCell(List.of(officialParagraph("감리내용", false, 18, "center")), "4560", 1)),
                520));
        if (items.isEmpty()) {
            rows.append(officialRow(List.of(
                    officialCell(officialParagraphs("", 18, "center"), "3100", 1),
                    officialCell(officialParagraphs("", 18, "center"), "2800", 1),
                    officialCell(officialParagraphs("", 18, "left"), "4560", 1)),
                    4820));
        } else {
            for (var item : items) {
                rows.append(officialRow(List.of(
                        officialCell(officialParagraphs(item.trade(), 18, "center"), "3100", 1),
                        officialCell(officialParagraphs(item.focus(), 18, "center"), "2800", 1),
                        officialCell(officialParagraphs(item.content(), 18, "left"), "4560", 1)),
                        officialSupervisionRowHeight(item)));
            }
        }
        return officialTableXml(
                List.of("3100", "2800", "4560"),
                rows.toString(),
                OFFICIAL_PAGE_WIDTH,
                8,
                0,
                8,
                0,
                4,
                4);
    }

    private int officialSupervisionRowHeight(OfficialSupervisionRow row) {
        var lineCount = Math.max(
                Math.max(lineCount(row.trade()), lineCount(row.focus())),
                lineCount(row.content()));
        return Math.max(420, 320 + (lineCount * 220));
    }

    private int lineCount(String value) {
        if (value == null || value.isBlank()) {
            return 1;
        }
        return value.split("\\R", -1).length;
    }

    private List<OfficialSupervisionRow> officialSupervisionRows(DocxRenderContext context) {
        var rows = new ArrayList<OfficialSupervisionRow>();
        rows.addAll(officialDailyItemsRows(context));
        if (!rows.isEmpty()) {
            return rows;
        }
        var answers = listValue(context.request().payload().get("checklistAnswers"));
        var fallbackTrade = joinedNonBlank(
                binding(context, "constructionTrade"),
                binding(context, "detailedProcess"),
                binding(context, "floor"));
        for (Object answer : answers) {
            var trade = firstNonBlank(checklistFieldValue(answer, "answer.trade"), fallbackTrade);
            var focus = firstNonBlank(checklistFieldValue(answer, "label"), binding(context, "supervisionItem"));
            var content = firstNonBlank(
                    checklistFieldValue(answer, "note"),
                    checklistFieldValue(answer, "answer.value"),
                    binding(context, "supervisionContent"));
            if (!trade.isBlank() || !focus.isBlank() || !content.isBlank()) {
                rows.add(new OfficialSupervisionRow(trade, focus, content));
            }
        }
        if (rows.isEmpty()) {
            var focus = binding(context, "supervisionItem", "supervisionFocus");
            var content = binding(context, "supervisionContent", "workDescription");
            if (!fallbackTrade.isBlank() || !focus.isBlank() || !content.isBlank()) {
                rows.add(new OfficialSupervisionRow(fallbackTrade, focus, content));
            }
        }
        return rows;
    }

    private List<OfficialSupervisionRow> officialDailyItemsRows(DocxRenderContext context) {
        var payload = context.request().payload();
        var steps = mapValue(payload.get("steps"));
        var dailyLog = mapValue(steps.get("DAILY_LOG"));
        var dailyLogPayload = mapValue(dailyLog.get("payload"));
        var dailyItems = mapValue(dailyLogPayload.get("dailyItems"));
        var groups = listValue(dailyItems.get("groups"));
        var rows = new ArrayList<OfficialSupervisionRow>();
        for (Object groupValue : groups) {
            var group = mapValue(groupValue);
            var groupLabel = officialGroupLabel(group);
            var entries = listValue(group.get("entries"));
            if (entries.isEmpty() && !groupLabel.isBlank()) {
                rows.add(new OfficialSupervisionRow(groupLabel, "", ""));
                continue;
            }
            var firstRowInGroup = true;
            for (Object entryValue : entries) {
                var entry = mapValue(entryValue);
                var focus = valueOrBlank(entry.get("inspectionItemName")).trim();
                var content = dailySupervisionContent(entry).trim();
                if (groupLabel.isBlank() && focus.isBlank() && content.isBlank()) {
                    continue;
                }
                rows.add(new OfficialSupervisionRow(firstRowInGroup ? groupLabel : "", focus, content));
                firstRowInGroup = false;
            }
        }
        return rows;
    }

    private String dailySupervisionContent(Map<String, Object> entry) {
        var content = valueOrBlank(entry.get("supervisionContent")).trim();
        if (!content.isBlank()) {
            return content;
        }
        var rows = new ArrayList<String>();
        for (Object rowValue : listValue(entry.get("checklistRows"))) {
            var row = mapValue(rowValue);
            var rowContent = dailyChecklistRowContent(row);
            if (!rowContent.isBlank()) {
                rows.add("- " + rowContent);
            }
        }
        if (rows.isEmpty()) {
            return "";
        }
        var title = valueOrBlank(entry.get("inspectionItemName")).trim();
        if (!title.isBlank()) {
            rows.add(0, title);
        }
        return String.join("\n", rows);
    }

    private String dailyChecklistRowContent(Map<String, Object> row) {
        var label = valueOrBlank(row.get("label")).trim();
        var result = dailyChecklistResultLabel(valueOrBlank(row.get("result")));
        var referenceNote = valueOrBlank(row.get("referenceNote")).trim();
        var actionNote = valueOrBlank(row.get("actionNote")).trim();
        var parts = new ArrayList<String>();
        if (!label.isBlank()) {
            parts.add(label);
        }
        if (!result.isBlank()) {
            parts.add(result);
        }
        if (!referenceNote.isBlank()) {
            parts.add("기준·참고: " + referenceNote);
        }
        if (!actionNote.isBlank()) {
            parts.add("조치사항: " + actionNote);
        }
        return parts.size() <= 1 ? "" : String.join(" / ", parts);
    }

    private String dailyChecklistResultLabel(String result) {
        return switch (result.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "COMPLIANT" -> "적합";
            case "NON_COMPLIANT" -> "부적합";
            default -> "";
        };
    }

    private String officialGroupLabel(Map<String, Object> group) {
        var trade = valueOrBlank(group.get("tradeName")).trim();
        var process = joinedNonBlank(
                valueOrBlank(group.get("processName")).trim(),
                valueOrBlank(group.get("floor")).trim());
        if (trade.isBlank()) {
            return process.isBlank() ? "" : "(" + process + ")";
        }
        return process.isBlank() ? trade : trade + "\n(" + process + ")";
    }

    private String officialLinedSectionXml(String title, String value, int heightTwips) {
        var paragraphs = new ArrayList<String>();
        paragraphs.add(officialParagraph(title, false, 18, "left"));
        paragraphs.addAll(officialParagraphs(value, 18, "left"));
        var row = officialRow(List.of(officialCell(paragraphs, String.valueOf(OFFICIAL_PAGE_WIDTH), 1)), heightTwips);
        return officialTableXml(
                List.of(String.valueOf(OFFICIAL_PAGE_WIDTH)),
                row,
                OFFICIAL_PAGE_WIDTH,
                8,
                0,
                3,
                0,
                0,
                0);
    }

    private String officialPhotoEvidenceXml(DocxRenderContext context) throws IOException {
        var photos = context.request().photos() == null ? List.<PhotoAsset>of() : context.request().photos();
        if (photos.isEmpty()) {
            return "";
        }
        var rows = new StringBuilder();
        rows.append(officialRow(List.of(
                officialCell(List.of(officialParagraph("사진 및 설명", true, 19, "center")), String.valueOf(OFFICIAL_PAGE_WIDTH), 2)),
                420));
        for (int i = 0; i < photos.size(); i += 2) {
            var left = photos.get(i);
            var cells = new ArrayList<String>();
            cells.add(officialPhotoCell(left, context));
            if (i + 1 < photos.size()) {
                cells.add(officialPhotoCell(photos.get(i + 1), context));
            } else {
                cells.add(officialCell(List.of(officialParagraph("", false, 18, "center")), "5230", 1));
            }
            rows.append(officialRow(cells, 3200));
        }
        return pageBreakParagraph()
                + officialTableXml(List.of("5230", "5230"), rows.toString(), OFFICIAL_PAGE_WIDTH, 4);
    }

    private String officialPhotoCell(PhotoAsset photo, DocxRenderContext context) throws IOException {
        var paragraphs = new ArrayList<String>();
        var image = resolvePhotoImage(photo, PhotoLayoutSize.MEDIUM, context);
        if (image.isPresent()) {
            paragraphs.add("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r>" + drawingXml(image.get()) + "</w:r></w:p>");
        } else {
            paragraphs.add(officialParagraph("이미지 없음", false, 18, "center"));
        }
        paragraphs.add(officialParagraph(officialPhotoCaption(photo), false, 18, "center"));
        return officialCell(paragraphs, "5230", 1);
    }

    private String officialPhotoCaption(PhotoAsset photo) {
        return firstNonBlank(photo.caption(), PhotoDisplayTexts.value(photo, "checklistItemKey"), "사진 " + valueOrBlank(photo.photoId()));
    }

    private String officialAuthoringGuideXml() {
        var guideLines = List.of(
                "1. 공종에는 주요공종 및 단위공종을 기재합니다.",
                "2. 감리착안사항은 공사감리의 주안점 및 점검계획을 기재합니다.",
                "3. 특기사항은 특별히 명기되어 있지 아니한 내용의 발생·조치사항 등을 기재합니다.",
                "4. 지적사항 및 처리결과는 재시공 및 공사중지 등 구두 또는 서면에 의한 지시내용과 처리결과를 기재합니다.",
                "",
                "※ 필수확인점에 해당하는 경우에는 반드시 작성하여야 합니다.");
        var bodyParagraphs = guideLines.stream()
                .map(line -> officialParagraph(line, false, 16, "left"))
                .toList();
        var rows = officialRow(List.of(
                officialCell(List.of(officialParagraph("작성방법", true, 17, "center")), String.valueOf(OFFICIAL_PAGE_WIDTH), 1, "BFBFBF", null, "center")),
                360);
        rows += officialRow(List.of(officialCell(bodyParagraphs, String.valueOf(OFFICIAL_PAGE_WIDTH), 1)), 1160);
        return officialTableXml(
                List.of(String.valueOf(OFFICIAL_PAGE_WIDTH)),
                rows,
                OFFICIAL_PAGE_WIDTH,
                8,
                0,
                8,
                0,
                3,
                0);
    }

    private String officialTableXml(List<String> columnWidths, String rows, int tableWidth, int borderSize) {
        return officialTableXml(columnWidths, rows, tableWidth, borderSize, borderSize, borderSize, borderSize, borderSize, borderSize);
    }

    private String officialTableXml(
            List<String> columnWidths,
            String rows,
            int tableWidth,
            int topBorder,
            int leftBorder,
            int bottomBorder,
            int rightBorder,
            int insideHBorder,
            int insideVBorder
    ) {
        return """
                <w:tbl>
                  <w:tblPr>
                    <w:tblW w:w="%d" w:type="dxa"/>
                    <w:tblBorders>
                      %s
                      %s
                      %s
                      %s
                      %s
                      %s
                    </w:tblBorders>
                  </w:tblPr>
                  <w:tblGrid>%s</w:tblGrid>
                  %s
                </w:tbl>
                """.formatted(
                tableWidth,
                officialBorderXml("top", topBorder),
                officialBorderXml("left", leftBorder),
                officialBorderXml("bottom", bottomBorder),
                officialBorderXml("right", rightBorder),
                officialBorderXml("insideH", insideHBorder),
                officialBorderXml("insideV", insideVBorder),
                tableGrid(columnWidths),
                rows);
    }

    private String officialBorderXml(String edge, int size) {
        if (size <= 0) {
            return "<w:%s w:val=\"nil\"/>".formatted(edge);
        }
        return "<w:%s w:val=\"single\" w:sz=\"%d\" w:space=\"0\" w:color=\"000000\"/>".formatted(edge, size);
    }

    private String officialRow(List<String> cells, int heightTwips) {
        var height = heightTwips > 0 ? "<w:trPr><w:trHeight w:val=\"" + heightTwips + "\"/></w:trPr>" : "";
        return "<w:tr>" + height + String.join("", cells) + "</w:tr>";
    }

    private String officialCell(List<String> paragraphs, String width, int gridSpan) {
        return officialCell(paragraphs, width, gridSpan, null, null, null);
    }

    private String officialCell(
            List<String> paragraphs,
            String width,
            int gridSpan,
            String fill,
            String verticalMerge,
            String verticalAlign
    ) {
        var gridSpanXml = gridSpan > 1 ? "<w:gridSpan w:val=\"" + gridSpan + "\"/>" : "";
        var fillXml = fill == null || fill.isBlank() ? "" : "<w:shd w:fill=\"" + fill + "\"/>";
        var mergeXml = switch (valueOrBlank(verticalMerge)) {
            case "restart" -> "<w:vMerge w:val=\"restart\"/>";
            case "continue" -> "<w:vMerge/>";
            default -> "";
        };
        var alignXml = verticalAlign == null || verticalAlign.isBlank()
                ? ""
                : "<w:vAlign w:val=\"" + escapeXml(verticalAlign) + "\"/>";
        return """
                <w:tc>
                  <w:tcPr>
                    <w:tcW w:w="%s" w:type="dxa"/>%s%s%s%s
                    <w:tcMar>
                      <w:top w:w="95" w:type="dxa"/>
                      <w:left w:w="110" w:type="dxa"/>
                      <w:bottom w:w="95" w:type="dxa"/>
                      <w:right w:w="110" w:type="dxa"/>
                    </w:tcMar>
                  </w:tcPr>
                  %s
                </w:tc>
                """.formatted(width, gridSpanXml, fillXml, mergeXml, alignXml, String.join("", paragraphs));
    }

    private List<String> officialParagraphs(String text, int sizeHalfPoints, String justification) {
        var value = valueOrBlank(text);
        if (value.isBlank()) {
            return List.of(officialParagraph("", false, sizeHalfPoints, justification));
        }
        return value.lines()
                .map(line -> officialParagraph(line, false, sizeHalfPoints, justification))
                .toList();
    }

    private String officialParagraph(String text, boolean bold, int sizeHalfPoints, String justification) {
        return officialParagraph(text, bold, sizeHalfPoints, justification, "000000");
    }

    private String officialParagraph(String text, boolean bold, int sizeHalfPoints, String justification, String color) {
        var jc = justification == null || justification.isBlank()
                ? ""
                : "<w:jc w:val=\"" + escapeXml(justification) + "\"/>";
        var boldXml = bold ? "<w:b/><w:bCs/>" : "";
        var colorXml = color == null || color.isBlank() ? "" : "<w:color w:val=\"" + escapeXml(color) + "\"/>";
        return """
                <w:p>
                  <w:pPr>%s<w:spacing w:before="0" w:after="0"/></w:pPr>
                  <w:r>
                    <w:rPr>
                      <w:rFonts w:ascii="Malgun Gothic" w:hAnsi="Malgun Gothic" w:eastAsia="맑은 고딕" w:cs="Malgun Gothic"/>
                      %s
                      %s
                      <w:sz w:val="%d"/><w:szCs w:val="%d"/>
                    </w:rPr>
                    <w:t xml:space="preserve">%s</w:t>
                  </w:r>
                </w:p>
                """.formatted(jc, boldXml, colorXml, sizeHalfPoints, sizeHalfPoints, escapeXml(text));
    }

    private String horizontalRuleParagraph(int size) {
        return """
                <w:p>
                  <w:pPr>
                    <w:pBdr><w:bottom w:val="single" w:sz="%d" w:space="1" w:color="000000"/></w:pBdr>
                    <w:spacing w:before="0" w:after="0"/>
                  </w:pPr>
                </w:p>
                """.formatted(size);
    }

    private String spacerParagraph(int heightTwips) {
        return """
                <w:p>
                  <w:pPr><w:spacing w:before="0" w:after="%d"/></w:pPr>
                </w:p>
                """.formatted(Math.max(0, heightTwips));
    }

    private String pageBreakParagraph() {
        return """
                <w:p>
                  <w:r><w:br w:type="page"/></w:r>
                </w:p>
                """;
    }

    private InspectionDateParts inspectionDateParts(DocxRenderContext context) {
        var year = binding(context, "inspectionYear");
        var month = binding(context, "inspectionMonth");
        var day = binding(context, "inspectionDay");
        var dayOfWeek = binding(context, "inspectionDayOfWeek", "dayOfWeek");
        if (!year.isBlank() || !month.isBlank() || !day.isBlank()) {
            return new InspectionDateParts(year, month, day, stripYoil(dayOfWeek));
        }
        var dateText = binding(context, "inspectionDate", "safetyInspectionDate", "reportDate");
        if (dateText.isBlank()) {
            return new InspectionDateParts("", "", "", stripYoil(dayOfWeek));
        }
        try {
            var date = LocalDate.parse(dateText.trim());
            return new InspectionDateParts(
                    String.valueOf(date.getYear()),
                    String.valueOf(date.getMonthValue()),
                    String.valueOf(date.getDayOfMonth()),
                    koreanDayOfWeek(date));
        } catch (DateTimeParseException ignored) {
            return new InspectionDateParts("", "", "", stripYoil(dayOfWeek));
        }
    }

    private String koreanDayOfWeek(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    private String stripYoil(String value) {
        var text = valueOrBlank(value).trim();
        if (text.endsWith("요일")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }

    private String binding(DocxRenderContext context, String... keys) {
        for (var key : keys) {
            var value = context.bindings().get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String joinedNonBlank(String... values) {
        return String.join(" / ", List.of(values).stream()
                .filter(value -> value != null && !value.isBlank())
                .toList());
    }

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String blankIfMissing(String value) {
        return value == null ? "" : value;
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

    private String applySignatureBlock(String xml, DocxRenderContext context) throws IOException {
        var hasPlaceholder = hasSignaturePlaceholder(xml);
        var shouldAppendDefault = shouldAppendDefaultSignatureBlock(context);
        if (!hasSignedSignature(context)) {
            var rendered = replaceWholeParagraphPlaceholder(xml, "${signatureBlock}", "");
            rendered = replaceWholeParagraphPlaceholder(rendered, "${signatureImage}", "");
            rendered = replaceWholeParagraphPlaceholder(rendered, "${writerSignature}", "");
            rendered = replaceWholeParagraphPlaceholder(rendered, "${inspectorSignature}", "");
            return rendered;
        }
        if (!hasPlaceholder && !shouldAppendDefault) {
            return xml;
        }
        var signatureBlock = signatureBlockXml(context);
        if (signatureBlock.isEmpty()) {
            return xml;
        }
        var rendered = xml;
        var before = rendered;
        rendered = replaceWholeParagraphPlaceholder(rendered, "${signatureBlock}", signatureBlock.get());
        rendered = replaceWholeParagraphPlaceholder(rendered, "${signatureImage}", signatureBlock.get());
        rendered = replaceWholeParagraphPlaceholder(rendered, "${writerSignature}", signatureBlock.get());
        rendered = replaceWholeParagraphPlaceholder(rendered, "${inspectorSignature}", signatureBlock.get());
        if (rendered.equals(before) && shouldAppendDefault) {
            rendered = appendBeforeBodyEnd(rendered, signatureBlock.get());
        }
        return rendered;
    }

    private boolean hasSignedSignature(DocxRenderContext context) {
        return Boolean.TRUE.equals(mapValue(context.request().payload().get("signature")).get("signed"));
    }

    private boolean hasSignaturePlaceholder(String xml) {
        return xml.contains("${signatureBlock}")
                || xml.contains("${signatureImage}")
                || xml.contains("${writerSignature}")
                || xml.contains("${inspectorSignature}")
                || paragraphsContainPlaceholder(xml, "${signatureBlock}")
                || paragraphsContainPlaceholder(xml, "${signatureImage}")
                || paragraphsContainPlaceholder(xml, "${writerSignature}")
                || paragraphsContainPlaceholder(xml, "${inspectorSignature}");
    }

    private boolean shouldAppendDefaultSignatureBlock(DocxRenderContext context) {
        var payload = context.request().payload();
        var report = mapValue(payload.get("report"));
        var documentType = mapValue(payload.get("documentType"));
        return isDailySupervisionType(stringValue(report.get("reportType")))
                || isDailySupervisionType(stringValue(documentType.get("reportType")))
                || isDailySupervisionType(stringValue(documentType.get("code")));
    }

    private boolean isDailySupervisionType(String value) {
        var code = normalizeCode(value);
        return "DAILY_SUPERVISION".equals(code) || code.contains("DAILY_SUPERVISION_LOG");
    }

    private String replaceWholeParagraphPlaceholder(String xml, String placeholder, String replacementXml) {
        if (!xml.contains(placeholder) && !paragraphsContainPlaceholder(xml, placeholder)) {
            return xml;
        }
        var matcher = WORD_PARAGRAPH_PATTERN.matcher(xml);
        var result = new StringBuffer();
        while (matcher.find()) {
            var paragraph = matcher.group();
            if (paragraph.contains(placeholder) || paragraphText(paragraph).contains(placeholder)) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacementXml));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private boolean paragraphsContainPlaceholder(String xml, String placeholder) {
        var matcher = WORD_PARAGRAPH_PATTERN.matcher(xml);
        while (matcher.find()) {
            if (paragraphText(matcher.group()).contains(placeholder)) {
                return true;
            }
        }
        return false;
    }

    private String appendBeforeBodyEnd(String xml, String blockXml) {
        var sectionIndex = xml.lastIndexOf("<w:sectPr");
        if (sectionIndex >= 0) {
            return xml.substring(0, sectionIndex) + blockXml + xml.substring(sectionIndex);
        }
        var bodyEnd = xml.lastIndexOf("</w:body>");
        if (bodyEnd >= 0) {
            return xml.substring(0, bodyEnd) + blockXml + xml.substring(bodyEnd);
        }
        return xml + blockXml;
    }

    private Optional<String> signatureBlockXml(DocxRenderContext context) throws IOException {
        var signature = mapValue(context.request().payload().get("signature"));
        if (!Boolean.TRUE.equals(signature.get("signed"))) {
            return Optional.empty();
        }
        var signedByName = stringValue(signature.get("signedByName"));
        var signedByRole = stringValue(signature.get("signedByRole"));
        var signedAt = stringValue(signature.get("signedAt"));
        var paragraphs = new ArrayList<String>();
        paragraphs.add(textParagraph("서명", true));
        var signer = signedByName == null ? "" : signedByName;
        if (signedByRole != null && !signedByRole.isBlank()) {
            signer = signer.isBlank() ? signedByRole : signer + " / " + signedByRole;
        }
        if (!signer.isBlank()) {
            paragraphs.add(textParagraph("서명자: " + signer));
        }
        signatureImageParagraph(context).ifPresentOrElse(
                paragraphs::add,
                () -> paragraphs.add(textParagraph("서명 이미지 없음")));
        if (signedAt != null && !signedAt.isBlank()) {
            paragraphs.add(textParagraph("서명일시: " + signedAt));
        }
        return Optional.of(String.join("", paragraphs));
    }

    private Optional<String> signatureImageParagraph(DocxRenderContext context) throws IOException {
        var content = signatureImageContent(context.request());
        if (content.isEmpty()) {
            return Optional.empty();
        }
        var image = context.addImage("archdox-signature", content.get(), SIGNATURE_WIDTH_EMU, SIGNATURE_HEIGHT_EMU);
        return Optional.of("<w:p><w:r>" + drawingXml(image) + "</w:r></w:p>");
    }

    private String officialSignatureMarkParagraph(
            DocxRenderContext context,
            String signatureSlot,
            boolean renderWhenSlotUnspecified
    ) throws IOException {
        var text = "(서명 또는 인)";
        var content = signatureImageContent(context.request());
        var signatureDrawing = "";
        if (content.isPresent() && shouldRenderSignatureSlot(context, signatureSlot, renderWhenSlotUnspecified)) {
            var image = context.addImage("archdox-signature", content.get(), SIGNATURE_WIDTH_EMU, SIGNATURE_HEIGHT_EMU);
            signatureDrawing = signatureAnchorDrawingXml(image);
        }
        return """
                <w:p>
                  <w:pPr>
                    <w:jc w:val="right"/>
                    <w:spacing w:before="0" w:after="0"/>
                  </w:pPr>
                  <w:r><w:t>%s</w:t></w:r>
                  %s
                </w:p>
                """.formatted(escapeXml(text), signatureDrawing.isBlank() ? "" : "<w:r>" + signatureDrawing + "</w:r>");
    }

    private boolean shouldRenderSignatureSlot(
            DocxRenderContext context,
            String signatureSlot,
            boolean renderWhenSlotUnspecified
    ) {
        var signature = mapValue(context.request().payload().get("signature"));
        var slots = listValue(signature.get("signatureSlots"));
        if (slots.isEmpty()) {
            return renderWhenSlotUnspecified;
        }
        for (var slot : slots) {
            if (signatureSlotMatches(slot, signatureSlot)) {
                return true;
            }
        }
        return false;
    }

    private boolean signatureSlotMatches(Object actualSlot, String expectedSlot) {
        var actual = valueOrBlank(actualSlot).trim();
        var expected = normalizeCode(expectedSlot);
        var code = normalizeCode(actual);
        if (code.equals("ALL") || code.equals(expected)) {
            return true;
        }
        if ("CHIEF_SUPERVISOR".equals(expected)) {
            return code.equals("TOTAL_SUPERVISOR")
                    || code.equals("SUPERVISOR")
                    || code.equals("PROJECT_MANAGER")
                    || code.equals("MANAGER")
                    || actual.contains("총괄")
                    || actual.contains("책임");
        }
        if ("ARCHITECT_ASSISTANT".equals(expected)) {
            return code.equals("ASSISTANT_SUPERVISOR")
                    || code.equals("ASSISTANT_ARCHITECT")
                    || code.equals("REPORT_WRITER")
                    || code.equals("WRITER")
                    || actual.contains("건축사보")
                    || actual.contains("감리사보");
        }
        return false;
    }

    private Optional<ResolvedPhotoContent> signatureImageContent(DocumentGenerationRequest request) {
        var signature = mapValue(request.payload().get("signature"));
        var dataUrl = stringValue(signature.get("imageDataUrl"));
        if (dataUrl == null || !dataUrl.startsWith("data:")) {
            return Optional.empty();
        }
        var comma = dataUrl.indexOf(',');
        if (comma < 0) {
            return Optional.empty();
        }
        var metadata = dataUrl.substring("data:".length(), comma).toLowerCase(Locale.ROOT);
        if (!metadata.endsWith(";base64")) {
            return Optional.empty();
        }
        var mimeType = metadata.substring(0, metadata.length() - ";base64".length());
        try {
            var bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
            if (bytes.length == 0) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedPhotoContent(bytes, mimeType));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String buildRichSectionXml(Map<String, Object> section, DocxRenderContext context) throws IOException {
        return switch (normalizeCode(stringValue(section.get("type")))) {
            case "PHOTO_TABLE" -> buildPhotoTableXml(section, context);
            case "CHECKLIST_TABLE" -> buildChecklistTableXml(section, context);
            case "CHECKLIST_PHOTO_TABLE" -> buildChecklistPhotoTableXml(section, context);
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
            if ("PHOTO_TABLE".equals(type) || "CHECKLIST_TABLE".equals(type) || "CHECKLIST_PHOTO_TABLE".equals(type)) {
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
        return buildChecklistLikeTableXml(section, checklistAnswers, "No checklist answers.", defaultChecklistTableFields());
    }

    private String buildChecklistPhotoTableXml(Map<String, Object> section, DocxRenderContext context) {
        var checklistPhotos = listValue(context.request().payload().get("checklistPhotos"));
        return buildChecklistLikeTableXml(section, checklistPhotos, "No checklist photos.", defaultChecklistPhotoTableFields());
    }

    private String buildChecklistLikeTableXml(
            Map<String, Object> section,
            List<?> rowsSource,
            String emptyFallback,
            List<Map<String, String>> defaultFields
    ) {
        var tableOptions = tableOptions(section);
        var fields = sectionFields(section, defaultFields);
        var columnWidths = checklistColumnWidths(section, fields, tableOptions.tableWidth());
        var rows = new StringBuilder();
        var title = stringValue(section.get("title"));
        if (includeTitle(section) && title != null && !title.isBlank()) {
            rows.append(tableRow(List.of(
                    tableCell(List.of(textParagraph(title, true)), String.valueOf(tableOptions.tableWidth()), fields.size(), tableOptions.titleFill()))));
        }
        rows.append(tableRow(checklistHeaderCells(fields, columnWidths, tableOptions)));
        if (rowsSource.isEmpty()) {
            rows.append(tableRow(List.of(
                    tableCell(
                            List.of(textParagraph(emptyText(section, emptyFallback))),
                            String.valueOf(tableOptions.tableWidth()),
                            fields.size()))));
        } else {
            for (var answer : rowsSource) {
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
        var seenValues = new HashSet<String>();
        for (var field : fields) {
            var value = photoFieldValue(photo, field.get("source"));
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!seenValues.add(value.trim())) {
                continue;
            }
            var label = PhotoDisplayTexts.label(field, photo);
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
                Map.of("label", "설명", "source", "caption"),
                Map.of("label", "항목", "source", "checklistItemKey"));
    }

    private List<Map<String, String>> defaultChecklistTableFields() {
        return List.of(
                Map.of("label", "Item", "source", "itemCode"),
                Map.of("label", "Label", "source", "label"),
                Map.of("label", "Answer", "source", "answer.value"),
                Map.of("label", "Photos", "source", "photoCount"),
                Map.of("label", "Note", "source", "note"));
    }

    private List<Map<String, String>> defaultChecklistPhotoTableFields() {
        return List.of(
                Map.of("label", "Item", "source", "itemCode"),
                Map.of("label", "Label", "source", "label"),
                Map.of("label", "Photos", "source", "photoCount"),
                Map.of("label", "Photo IDs", "source", "photoIds"));
    }

    private String photoFieldValue(PhotoAsset photo, String source) {
        return PhotoDisplayTexts.value(photo, source);
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
                <w:drawing xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                           xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                           xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"
                           xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <wp:inline distT="0" distB="0" distL="0" distR="0">
                    <wp:extent cx="%d" cy="%d"/>
                    <wp:docPr id="%d" name="ArchDox Photo %d"/>
                    <wp:cNvGraphicFramePr>
                      <a:graphicFrameLocks noChangeAspect="1"/>
                    </wp:cNvGraphicFramePr>
                    <a:graphic>
                      <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                        <pic:pic>
                          <pic:nvPicPr>
                            <pic:cNvPr id="%d" name="%s"/>
                            <pic:cNvPicPr/>
                          </pic:nvPicPr>
                          <pic:blipFill>
                            <a:blip r:embed="%s"/>
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

    private String signatureAnchorDrawingXml(DocxImage image) {
        return """
                <w:drawing xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                           xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                           xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"
                           xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <wp:anchor distT="0" distB="0" distL="0" distR="0"
                             simplePos="0" relativeHeight="251659264"
                             behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1">
                    <wp:simplePos x="0" y="0"/>
                    <wp:positionH relativeFrom="column">
                      <wp:align>right</wp:align>
                    </wp:positionH>
                    <wp:positionV relativeFrom="paragraph">
                      <wp:posOffset>-330000</wp:posOffset>
                    </wp:positionV>
                    <wp:extent cx="%d" cy="%d"/>
                    <wp:effectExtent l="0" t="0" r="0" b="0"/>
                    <wp:wrapNone/>
                    <wp:docPr id="%d" name="ArchDox Signature %d"/>
                    <wp:cNvGraphicFramePr>
                      <a:graphicFrameLocks noChangeAspect="1"/>
                    </wp:cNvGraphicFramePr>
                    <a:graphic>
                      <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                        <pic:pic>
                          <pic:nvPicPr>
                            <pic:cNvPr id="%d" name="%s"/>
                            <pic:cNvPicPr/>
                          </pic:nvPicPr>
                          <pic:blipFill>
                            <a:blip r:embed="%s"/>
                            <a:stretch><a:fillRect/></a:stretch>
                          </pic:blipFill>
                          <pic:spPr>
                            <a:xfrm><a:off x="0" y="0"/><a:ext cx="%d" cy="%d"/></a:xfrm>
                            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                          </pic:spPr>
                        </pic:pic>
                      </a:graphicData>
                    </a:graphic>
                  </wp:anchor>
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
        addDefaultSignatureAliases(bindings);
        return bindings;
    }

    private void addDefaultSignatureAliases(Map<String, String> bindings) {
        bindings.putIfAbsent("signedByName", "");
        bindings.putIfAbsent("signedByRole", "");
        bindings.putIfAbsent("signedAt", "");
        bindings.putIfAbsent("signatureSignedByName", "");
        bindings.putIfAbsent("signatureSignedByRole", "");
        bindings.putIfAbsent("signatureSignedAt", "");
        bindings.putIfAbsent("signature.signedByName", "");
        bindings.putIfAbsent("signature.signedByRole", "");
        bindings.putIfAbsent("signature.signedAt", "");
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
        return imageExtension(content, photo.storageRef());
    }

    private String imageExtension(ResolvedPhotoContent content, String storageRef) {
        var mimeType = content.mimeType() == null ? "" : content.mimeType().toLowerCase(Locale.ROOT);
        return switch (mimeType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/jpg", "image/jpeg" -> "jpeg";
            default -> extensionFromStorageRef(storageRef).orElse("jpeg");
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
            return addImage("archdox-photo", photo.storageRef(), content, imageSize == null ? photo.layoutSize() : imageSize);
        }

        private DocxImage addImage(String fileNamePrefix, ResolvedPhotoContent content, PhotoLayoutSize imageSize) {
            return addImage(fileNamePrefix, fileNamePrefix, content, imageSize);
        }

        private DocxImage addImage(String fileNamePrefix, ResolvedPhotoContent content, long widthEmu, long heightEmu) {
            return addImage(fileNamePrefix, fileNamePrefix, content, widthEmu, heightEmu);
        }

        private DocxImage addImage(
                String fileNamePrefix,
                String storageRef,
                ResolvedPhotoContent content,
                PhotoLayoutSize imageSize
        ) {
            var size = imageSize(imageSize);
            return addImage(fileNamePrefix, storageRef, content, size[0], size[1]);
        }

        private DocxImage addImage(
                String fileNamePrefix,
                String storageRef,
                ResolvedPhotoContent content,
                long widthEmu,
                long heightEmu
        ) {
            imageCounter++;
            var extension = imageExtension(content, storageRef);
            var fileName = fileNamePrefix + "-" + imageCounter + "." + extension;
            var path = "word/media/" + fileName;
            while (mediaPaths.contains(path)) {
                imageCounter++;
                fileName = fileNamePrefix + "-" + imageCounter + "." + extension;
                path = "word/media/" + fileName;
            }
            mediaPaths.add(path);
            var relationshipId = "rIdArchDoxImage" + imageCounter;
            relationships.add(new DocxRelationship(relationshipId, "media/" + fileName));
            contentTypeDefaults.putIfAbsent(extension, imageContentType(extension, content));
            media.add(new DocxMedia(path, content.content()));
            return new DocxImage(relationshipId, fileName, imageCounter, widthEmu, heightEmu);
        }
    }

    private record InspectionDateParts(String year, String month, String day, String dayOfWeek) {
    }

    private record OfficialSupervisionRow(String trade, String focus, String content) {
        private static OfficialSupervisionRow blank() {
            return new OfficialSupervisionRow("", "", "");
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

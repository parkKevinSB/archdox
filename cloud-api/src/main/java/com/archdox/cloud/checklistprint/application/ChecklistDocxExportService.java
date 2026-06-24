package com.archdox.cloud.checklistprint.application;

import com.archdox.cloud.checklistprint.dto.ChecklistDocxExport;
import com.archdox.cloud.checklistprint.dto.ChecklistPrintDocumentResponse;
import com.archdox.cloud.checklistprint.dto.ChecklistPrintResponse;
import com.archdox.cloud.checklistprint.dto.ChecklistPrintRowResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class ChecklistDocxExportService {
    private static final String TYPE_PHASE = "PHASE";
    private static final int TABLE_WIDTH = 9940;
    private static final int BODY_FONT_SIZE = 17;
    private static final int HEADER_FONT_SIZE = 18;
    private static final int RESULT_FONT_SIZE = 20;
    private static final String DOCX_CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
              <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
            </Types>
            """;
    private static final String PACKAGE_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
            """;
    private static final String DOCUMENT_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
            """;
    private static final String STYLES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:docDefaults>
                <w:rPrDefault>
                  <w:rPr>
                    <w:rFonts w:ascii="Malgun Gothic" w:eastAsia="Malgun Gothic" w:hAnsi="Malgun Gothic"/>
                    <w:lang w:val="ko-KR" w:eastAsia="ko-KR"/>
                    <w:sz w:val="18"/>
                  </w:rPr>
                </w:rPrDefault>
                <w:pPrDefault>
                  <w:pPr>
                    <w:spacing w:before="0" w:after="0" w:line="240" w:lineRule="auto"/>
                  </w:pPr>
                </w:pPrDefault>
              </w:docDefaults>
              <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
                <w:name w:val="Normal"/>
                <w:rPr><w:rFonts w:ascii="Malgun Gothic" w:eastAsia="Malgun Gothic" w:hAnsi="Malgun Gothic"/><w:sz w:val="18"/></w:rPr>
              </w:style>
            </w:styles>
            """;

    private final ChecklistPrintReadService readService;

    public ChecklistDocxExportService(ChecklistPrintReadService readService) {
        this.readService = readService;
    }

    public ChecklistDocxExport export(Long reportId, String checklistType, UserPrincipal principal) {
        var preview = readService.preview(reportId, checklistType, principal);
        var content = createDocx(renderDocumentXml(preview));
        return new ChecklistDocxExport(fileName(preview), content);
    }

    public ChecklistDocxExport exportSystem(Long officeId, Long reportId, String checklistType) {
        var preview = readService.previewSystem(officeId, reportId, checklistType);
        var content = createDocx(renderDocumentXml(preview));
        return new ChecklistDocxExport(fileName(preview), content);
    }

    private byte[] createDocx(String documentXml) {
        try {
            var output = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                put(zip, "[Content_Types].xml", DOCX_CONTENT_TYPES);
                put(zip, "_rels/.rels", PACKAGE_RELS);
                put(zip, "word/_rels/document.xml.rels", DOCUMENT_RELS);
                put(zip, "word/styles.xml", STYLES);
                put(zip, "word/document.xml", documentXml);
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Checklist DOCX export failed", ex);
        }
    }

    private void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String renderDocumentXml(ChecklistPrintResponse response) {
        var body = new StringBuilder();
        if (response.documents().isEmpty()) {
            paragraph(body, "출력할 체크리스트가 없습니다.", true, "center", 24);
        }
        var first = true;
        for (var document : response.documents()) {
            if (!first) {
                pageBreak(body);
            }
            first = false;
            if (TYPE_PHASE.equals(document.checklistType())) {
                phaseDocument(body, response, document);
            } else {
                tradeDocument(body, document);
            }
        }
        body.append(sectionProperties());
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                """
                + body
                + """
                  </w:body>
                </w:document>
                """;
    }

    private void tradeDocument(StringBuilder xml, ChecklistPrintDocumentResponse document) {
        paragraph(xml, "공종별 감리 체크리스트", true, "center", 30);
        table(xml, List.of(
                List.of(headerCell("공종", 1100), cell(document.tradeName(), 3900), headerCell("문서번호", 1200), cell(document.documentNo(), 3740)),
                List.of(headerCell("세부공종", 1100), cell(blankAsDash(document.subTradeName()), 3900), headerCell("부위", 1200), cell(blankAsDash(document.floorArea()), 3740)),
                List.of(headerCell("층", 1100), cell(blankAsDash(document.floorArea()), 3900), headerCell("위치", 1200), cell(blankAsDash(document.location()), 3740))
        ), false);
        table(xml, tradeRows(document), true);
        signatureForTrade(xml);
    }

    private void phaseDocument(StringBuilder xml, ChecklistPrintResponse response, ChecklistPrintDocumentResponse document) {
        paragraph(xml, "단계별 감리업무 Check List", true, "center", 30);
        table(xml, List.of(
                List.of(headerCell("공사명", 1100), cell(firstNonBlank(response.reportTitle(), response.reportNo()), 3900), headerCell("문서번호", 1200), cell(document.documentNo(), 3740)),
                List.of(headerCell("건축주", 1100), cell("", 3900), headerCell("발행일시", 1200), cell("", 3740)),
                List.of(headerCell("공사단계", 1100), cell(document.constructionPhaseName(), 3900), headerCell("업무구분", 1200), cell(document.supervisionWorkModeName(), 3740))
        ), false);
        table(xml, phaseRows(document), true);
        paragraph(xml, "- 상기와 같이 단계별 감리업무에 따른 검토 사항을 확인하여 제출합니다.", false, "center", 20);
        signatureForPhase(xml);
    }

    private List<List<Cell>> tradeRows(ChecklistPrintDocumentResponse document) {
        var rows = new ArrayList<List<Cell>>();
        rows.add(List.of(
                headerCell("구분", 780),
                headerCell("세부공정", 1080),
                headerCell("검사항목", 3180),
                headerCell("기준·참고사항", 2000),
                headerCell("적합", 520),
                headerCell("부적합", 520),
                headerCell("조치사항", 1860)
        ));
        for (var index = 0; index < document.rows().size(); index++) {
            var row = document.rows().get(index);
            var categoryStart = isFirstWorkCategory(document.rows(), index);
            var processStart = isFirstProcess(document.rows(), index);
            rows.add(List.of(
                    verticalCenterCell(categoryStart ? row.workCategoryName() : "", 780, categoryStart),
                    verticalCell(processStart ? row.processName() : "", 1080, processStart),
                    cell(row.rowLabel(), 3180),
                    cell(joinNonBlank(row.basis(), row.referenceNote()), 2000),
                    resultCell(row, "COMPLIANT", 520),
                    resultCell(row, "NON_COMPLIANT", 520),
                    cell(row.actionNote(), 1860)
            ));
        }
        return rows;
    }

    private List<List<Cell>> phaseRows(ChecklistPrintDocumentResponse document) {
        var rows = new ArrayList<List<Cell>>();
        rows.add(List.of(
                headerCell("검토항목", 1400),
                headerCell("세부검토사항", 5400),
                headerCell("적합", 560),
                headerCell("부적합", 560),
                headerCell("조치사항", 2020)
        ));
        for (var index = 0; index < document.rows().size(); index++) {
            var row = document.rows().get(index);
            var processStart = isFirstProcess(document.rows(), index);
            rows.add(List.of(
                    verticalCenterCell(processStart ? row.processName() : "", 1400, processStart),
                    cell(joinNonBlank(row.rowLabel(), row.referenceNote()), 5400),
                    resultCell(row, "COMPLIANT", 560),
                    resultCell(row, "NON_COMPLIANT", 560),
                    cell(row.actionNote(), 2020)
            ));
        }
        return rows;
    }

    private void signatureForTrade(StringBuilder xml) {
        table(xml, List.of(
                List.of(headerCell("시공자점검일", 1600), centerCell("년    월    일", 3000), headerCell("총괄 시공 책임자\n(또는 현장관리인)", 2700), centerCell("(인)", 2640)),
                List.of(headerCell("", 1600), cell("", 3000), headerCell("공종별 시공 관리자", 2700), centerCell("(인)", 2640)),
                List.of(headerCell("감리자점검일", 1600), centerCell("년    월    일", 3000), headerCell("총괄 감리 책임자", 2700), centerCell("(인)", 2640)),
                List.of(headerCell("", 1600), cell("", 3000), headerCell("건축사보\n(공종별 감리 책임자)", 2700), centerCell("(인)", 2640)),
                List.of(headerCell("첨부자료", 1600), cell("", 3000), cell("", 2700), cell("", 2640))
        ), false);
    }

    private void signatureForPhase(StringBuilder xml) {
        table(xml, List.of(
                List.of(cell("", 5700), headerCell("총괄 감리 책임자", 2700), centerCell("(인)", 1540)),
                List.of(cell("", 5700), headerCell("건축사보\n(공종별 감리 책임자)", 2700), centerCell("(인)", 1540))
        ), false);
    }

    private void table(StringBuilder xml, List<List<Cell>> rows) {
        table(xml, rows, false);
    }

    private void table(StringBuilder xml, List<List<Cell>> rows, boolean repeatHeader) {
        var widths = rows.isEmpty() ? List.<Integer>of() : rows.get(0).stream().map(Cell::width).toList();
        var tableWidth = widths.stream().mapToInt(Integer::intValue).sum();
        xml.append("<w:tbl><w:tblPr>");
        xml.append("<w:tblW w:w=\"").append(tableWidth > 0 ? tableWidth : TABLE_WIDTH).append("\" w:type=\"dxa\"/>");
        xml.append("<w:jc w:val=\"center\"/>");
        xml.append("<w:tblLayout w:type=\"fixed\"/>");
        xml.append("<w:tblCellMar><w:top w:w=\"80\" w:type=\"dxa\"/><w:left w:w=\"90\" w:type=\"dxa\"/>");
        xml.append("<w:bottom w:w=\"80\" w:type=\"dxa\"/><w:right w:w=\"90\" w:type=\"dxa\"/></w:tblCellMar>");
        xml.append("<w:tblBorders><w:top w:val=\"single\" w:sz=\"8\"/><w:left w:val=\"single\" w:sz=\"8\"/>");
        xml.append("<w:bottom w:val=\"single\" w:sz=\"8\"/><w:right w:val=\"single\" w:sz=\"8\"/>");
        xml.append("<w:insideH w:val=\"single\" w:sz=\"4\"/><w:insideV w:val=\"single\" w:sz=\"4\"/></w:tblBorders>");
        xml.append("</w:tblPr>");
        if (!widths.isEmpty()) {
            xml.append("<w:tblGrid>");
            for (var width : widths) {
                xml.append("<w:gridCol w:w=\"").append(width).append("\"/>");
            }
            xml.append("</w:tblGrid>");
        }
        for (var index = 0; index < rows.size(); index++) {
            xml.append("<w:tr><w:trPr>");
            if (repeatHeader && index == 0) {
                xml.append("<w:tblHeader/>");
            }
            xml.append("<w:trHeight w:val=\"320\" w:hRule=\"atLeast\"/>");
            xml.append("</w:trPr>");
            var row = rows.get(index);
            for (var cell : row) {
                tableCell(xml, cell);
            }
            xml.append("</w:tr>");
        }
        xml.append("</w:tbl>");
        paragraph(xml, "", false, "left", 6);
    }

    private void tableCell(StringBuilder xml, Cell cell) {
        xml.append("<w:tc><w:tcPr>");
        xml.append("<w:tcW w:w=\"").append(cell.width()).append("\" w:type=\"dxa\"/>");
        if (!cell.fill().isBlank()) {
            xml.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"").append(cell.fill()).append("\"/>");
        }
        if (!cell.verticalMerge().isBlank()) {
            if ("restart".equals(cell.verticalMerge())) {
                xml.append("<w:vMerge w:val=\"restart\"/>");
            } else {
                xml.append("<w:vMerge/>");
            }
        }
        xml.append("<w:vAlign w:val=\"center\"/></w:tcPr>");
        var lines = cell.text().isBlank() ? List.of("") : List.of(cell.text().split("\\R", -1));
        for (var line : lines) {
            xml.append("<w:p><w:pPr>");
            xml.append("<w:spacing w:before=\"0\" w:after=\"0\" w:line=\"230\" w:lineRule=\"auto\"/>");
            if (!cell.justification().isBlank()) {
                xml.append("<w:jc w:val=\"").append(cell.justification()).append("\"/>");
            }
            if (line.startsWith("-")) {
                xml.append("<w:ind w:left=\"160\" w:hanging=\"120\"/>");
            }
            xml.append("</w:pPr><w:r><w:rPr>");
            xml.append("<w:rFonts w:ascii=\"Malgun Gothic\" w:eastAsia=\"Malgun Gothic\" w:hAnsi=\"Malgun Gothic\"/>");
            if (cell.bold()) {
                xml.append("<w:b/>");
            }
            xml.append("<w:sz w:val=\"").append(cell.fontSize()).append("\"/></w:rPr><w:t xml:space=\"preserve\">");
            xml.append(escapeXml(line));
            xml.append("</w:t></w:r></w:p>");
        }
        xml.append("</w:tc>");
    }

    private void paragraph(StringBuilder xml, String text, boolean bold, String justification, int size) {
        xml.append("<w:p><w:pPr>");
        xml.append("<w:spacing w:before=\"0\" w:after=\"").append(size >= 24 ? 160 : 80).append("\"/>");
        if (!justification.isBlank()) {
            xml.append("<w:jc w:val=\"").append(justification).append("\"/>");
        }
        xml.append("</w:pPr><w:r><w:rPr>");
        xml.append("<w:rFonts w:ascii=\"Malgun Gothic\" w:eastAsia=\"Malgun Gothic\" w:hAnsi=\"Malgun Gothic\"/>");
        if (bold) {
            xml.append("<w:b/>");
        }
        xml.append("<w:sz w:val=\"").append(size).append("\"/></w:rPr><w:t xml:space=\"preserve\">");
        xml.append(escapeXml(text));
        xml.append("</w:t></w:r></w:p>");
    }

    private void pageBreak(StringBuilder xml) {
        xml.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>");
    }

    private String sectionProperties() {
        return """
                <w:sectPr>
                  <w:pgSz w:w="11906" w:h="16838"/>
                  <w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720" w:header="450" w:footer="450" w:gutter="0"/>
                  <w:docGrid w:linePitch="360"/>
                </w:sectPr>
                """;
    }

    private Cell headerCell(String text, int width) {
        return new Cell(nullToBlank(text), width, "center", "", HEADER_FONT_SIZE, true, "E5E7EB");
    }

    private Cell cell(String text, int width) {
        return new Cell(nullToBlank(text), width, "left", "", BODY_FONT_SIZE, false, "");
    }

    private Cell centerCell(String text, int width) {
        return new Cell(nullToBlank(text), width, "center", "", BODY_FONT_SIZE, false, "");
    }

    private Cell verticalCell(String text, int width, boolean restart) {
        return new Cell(nullToBlank(text), width, "left", restart ? "restart" : "continue", BODY_FONT_SIZE, false, "");
    }

    private Cell verticalCenterCell(String text, int width, boolean restart) {
        return new Cell(nullToBlank(text), width, "center", restart ? "restart" : "continue", BODY_FONT_SIZE, false, "");
    }

    private Cell resultCell(ChecklistPrintRowResponse row, String expected, int width) {
        return new Cell(expected.equals(row.result()) ? "○" : "", width, "center", "", RESULT_FONT_SIZE, true, "");
    }

    private String fileName(ChecklistPrintResponse response) {
        var reportKey = firstNonBlank(response.reportNo(), String.valueOf(response.reportId()));
        var safeReportKey = reportKey.replaceAll("[^A-Za-z0-9._-]", "_");
        var type = response.checklistType().toLowerCase(Locale.ROOT);
        return "archdox-checklist-" + safeReportKey + "-" + type + ".docx";
    }

    private String joinNonBlank(String first, String second) {
        var left = nullToBlank(first).trim();
        var right = nullToBlank(second).trim();
        if (left.isBlank()) {
            return right;
        }
        if (right.isBlank()) {
            return left;
        }
        return left + "\n" + right;
    }

    private String firstNonBlank(String first, String second) {
        var left = nullToBlank(first).trim();
        return left.isBlank() ? nullToBlank(second).trim() : left;
    }

    private String blankAsDash(String value) {
        var text = nullToBlank(value).trim();
        return text.isBlank() || "NONE".equalsIgnoreCase(text) ? "-" : text;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String escapeXml(String value) {
        return nullToBlank(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private boolean isFirstWorkCategory(List<ChecklistPrintRowResponse> rows, int index) {
        return index == 0 || !Objects.equals(
                rows.get(index - 1).workCategoryCode(),
                rows.get(index).workCategoryCode());
    }

    private boolean isFirstProcess(List<ChecklistPrintRowResponse> rows, int index) {
        return index == 0
                || !Objects.equals(rows.get(index - 1).workCategoryCode(), rows.get(index).workCategoryCode())
                || !Objects.equals(rows.get(index - 1).processCode(), rows.get(index).processCode());
    }

    private record Cell(
            String text,
            int width,
            String justification,
            String verticalMerge,
            int fontSize,
            boolean bold,
            String fill
    ) {
    }
}

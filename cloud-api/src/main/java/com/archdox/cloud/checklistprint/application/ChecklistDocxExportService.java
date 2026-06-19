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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class ChecklistDocxExportService {
    private static final String TYPE_PHASE = "PHASE";
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
              <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
                <w:name w:val="Normal"/>
                <w:rPr><w:rFonts w:ascii="Malgun Gothic" w:eastAsia="Malgun Gothic" w:hAnsi="Malgun Gothic"/><w:sz w:val="20"/></w:rPr>
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
                List.of(headerCell("공종", 900), cell(document.tradeName(), 3600), headerCell("문서번호", 1100), cell(document.documentNo(), 2200)),
                List.of(headerCell("세부공종", 900), cell(blankAsDash(document.subTradeName()), 3600), headerCell("부위", 1100), cell(blankAsDash(document.floorArea()), 2200)),
                List.of(headerCell("층", 900), cell(blankAsDash(document.floorArea()), 3600), headerCell("위치", 1100), cell(blankAsDash(document.location()), 2200))
        ));
        table(xml, tradeRows(document));
        signatureForTrade(xml);
    }

    private void phaseDocument(StringBuilder xml, ChecklistPrintResponse response, ChecklistPrintDocumentResponse document) {
        paragraph(xml, "단계별 감리업무 Check List", true, "center", 30);
        table(xml, List.of(
                List.of(headerCell("공사명", 1200), cell(firstNonBlank(response.reportTitle(), response.reportNo()), 3800), headerCell("문서번호", 1200), cell(document.documentNo(), 2200)),
                List.of(headerCell("건축주", 1200), cell("", 3800), headerCell("발행일시", 1200), cell("", 2200)),
                List.of(headerCell("공사단계", 1200), cell(document.constructionPhaseName(), 3800), headerCell("업무구분", 1200), cell(document.supervisionWorkModeName(), 2200))
        ));
        table(xml, phaseRows(document));
        paragraph(xml, "- 상기와 같이 단계별 감리업무에 따른 검토 사항을 확인하여 제출합니다.", false, "center", 20);
        signatureForPhase(xml);
    }

    private List<List<Cell>> tradeRows(ChecklistPrintDocumentResponse document) {
        var rows = new ArrayList<List<Cell>>();
        rows.add(List.of(
                headerCell("구분", 750),
                headerCell("세부공정", 1000),
                headerCell("검사항목", 2700),
                headerCell("기준·참고사항", 1900),
                headerCell("적합", 550),
                headerCell("부적합", 550),
                headerCell("조치사항", 1500)
        ));
        for (var row : document.rows()) {
            rows.add(List.of(
                    centerCell(row.workCategoryName(), 750),
                    cell(row.processName(), 1000),
                    cell(row.rowLabel(), 2700),
                    cell(joinNonBlank(row.basis(), row.referenceNote()), 1900),
                    resultCell(row, "COMPLIANT", 550),
                    resultCell(row, "NON_COMPLIANT", 550),
                    cell(row.actionNote(), 1500)
            ));
        }
        return rows;
    }

    private List<List<Cell>> phaseRows(ChecklistPrintDocumentResponse document) {
        var rows = new ArrayList<List<Cell>>();
        rows.add(List.of(
                headerCell("검토항목", 1400),
                headerCell("세부검토사항", 5200),
                headerCell("적합", 600),
                headerCell("부적합", 600),
                headerCell("조치사항", 1700)
        ));
        for (var row : document.rows()) {
            rows.add(List.of(
                    centerCell(row.processName(), 1400),
                    cell(joinNonBlank(row.rowLabel(), row.referenceNote()), 5200),
                    resultCell(row, "COMPLIANT", 600),
                    resultCell(row, "NON_COMPLIANT", 600),
                    cell(row.actionNote(), 1700)
            ));
        }
        return rows;
    }

    private void signatureForTrade(StringBuilder xml) {
        table(xml, List.of(
                List.of(headerCell("시공자점검일", 1600), centerCell("년    월    일", 3000), headerCell("총괄 시공 책임자\n(또는 현장관리인)", 2600), cell("(인)", 900)),
                List.of(headerCell("", 1600), cell("", 3000), headerCell("공종별 시공 관리자", 2600), cell("(인)", 900)),
                List.of(headerCell("감리자점검일", 1600), centerCell("년    월    일", 3000), headerCell("총괄 감리 책임자", 2600), cell("(인)", 900)),
                List.of(headerCell("", 1600), cell("", 3000), headerCell("건축사보\n(공종별 감리 책임자)", 2600), cell("(인)", 900)),
                List.of(headerCell("첨부자료", 1600), cell("", 6500))
        ));
    }

    private void signatureForPhase(StringBuilder xml) {
        table(xml, List.of(
                List.of(cell("", 5200), headerCell("총괄 감리 책임자", 2500), cell("(인)", 900)),
                List.of(cell("", 5200), headerCell("건축사보\n(공종별 감리 책임자)", 2500), cell("(인)", 900))
        ));
    }

    private void table(StringBuilder xml, List<List<Cell>> rows) {
        xml.append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/>");
        xml.append("<w:tblBorders><w:top w:val=\"single\" w:sz=\"8\"/><w:left w:val=\"single\" w:sz=\"8\"/>");
        xml.append("<w:bottom w:val=\"single\" w:sz=\"8\"/><w:right w:val=\"single\" w:sz=\"8\"/>");
        xml.append("<w:insideH w:val=\"single\" w:sz=\"4\"/><w:insideV w:val=\"single\" w:sz=\"4\"/></w:tblBorders>");
        xml.append("</w:tblPr>");
        for (var row : rows) {
            xml.append("<w:tr>");
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
        if (cell.header()) {
            xml.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"E5E7EB\"/>");
        }
        xml.append("<w:vAlign w:val=\"center\"/></w:tcPr>");
        var lines = cell.text().isBlank() ? List.of("") : List.of(cell.text().split("\\R", -1));
        for (var line : lines) {
            xml.append("<w:p><w:pPr>");
            if (!cell.justification().isBlank()) {
                xml.append("<w:jc w:val=\"").append(cell.justification()).append("\"/>");
            }
            xml.append("</w:pPr><w:r><w:rPr>");
            xml.append("<w:rFonts w:ascii=\"Malgun Gothic\" w:eastAsia=\"Malgun Gothic\" w:hAnsi=\"Malgun Gothic\"/>");
            if (cell.header()) {
                xml.append("<w:b/>");
            }
            xml.append("<w:sz w:val=\"18\"/></w:rPr><w:t xml:space=\"preserve\">");
            xml.append(escapeXml(line));
            xml.append("</w:t></w:r></w:p>");
        }
        xml.append("</w:tc>");
    }

    private void paragraph(StringBuilder xml, String text, boolean bold, String justification, int size) {
        xml.append("<w:p><w:pPr>");
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
                </w:sectPr>
                """;
    }

    private Cell headerCell(String text, int width) {
        return new Cell(nullToBlank(text), width, true, "center");
    }

    private Cell cell(String text, int width) {
        return new Cell(nullToBlank(text), width, false, "left");
    }

    private Cell centerCell(String text, int width) {
        return new Cell(nullToBlank(text), width, false, "center");
    }

    private Cell resultCell(ChecklistPrintRowResponse row, String expected, int width) {
        return centerCell(expected.equals(row.result()) ? "○" : "", width);
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

    private record Cell(
            String text,
            int width,
            boolean header,
            String justification
    ) {
    }
}

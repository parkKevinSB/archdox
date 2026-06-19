package com.archdox.cloud.checklistprint.application;

import com.archdox.cloud.checklistprint.dto.ChecklistPrintDocumentResponse;
import com.archdox.cloud.checklistprint.dto.ChecklistPrintResponse;
import com.archdox.cloud.checklistprint.dto.ChecklistPrintRowResponse;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStatus;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.supervisioncatalog.application.SupervisionDomainCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChecklistPrintReadService {
    private static final String REPORT_TYPE_DAILY = "CONSTRUCTION_DAILY_SUPERVISION_LOG";
    private static final String REPORT_TYPE_CHECKLIST = "CONSTRUCTION_SUPERVISION_CHECKLIST";
    private static final String BASIC_INFO_STEP = "BASIC_INFO";
    private static final String CHECKLIST_SOURCE_STEP = "CHECKLIST_SOURCE";
    private static final String DAILY_LOG_STEP = "DAILY_LOG";
    private static final String FIELD_CHECKLIST_SELECTION = "checklistSelection";
    private static final String MODE_ALL_SITE = "ALL_SITE";
    private static final String MODE_DATE_RANGE = "DATE_RANGE";
    private static final String MODE_SELECTED_REPORTS = "SELECTED_REPORTS";
    private static final String TYPE_TRADE = "TRADE";
    private static final String TYPE_PHASE = "PHASE";
    private static final String TYPE_ALL = "ALL";

    private final InspectionReportRepository reportRepository;
    private final InspectionReportStepRepository stepRepository;
    private final OfficePermissionService permissionService;
    private final SupervisionDomainCatalogService catalogService;
    private final ObjectMapper objectMapper;

    public ChecklistPrintReadService(
            InspectionReportRepository reportRepository,
            InspectionReportStepRepository stepRepository,
            OfficePermissionService permissionService,
            SupervisionDomainCatalogService catalogService,
            ObjectMapper objectMapper
    ) {
        this.reportRepository = reportRepository;
        this.stepRepository = stepRepository;
        this.permissionService = permissionService;
        this.catalogService = catalogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ChecklistPrintResponse preview(Long reportId, String checklistType, UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        return previewInternal(officeId, reportId, checklistType, principal);
    }

    @Transactional(readOnly = true)
    public ChecklistPrintResponse previewSystem(Long officeId, Long reportId, String checklistType) {
        return previewInternal(officeId, reportId, checklistType, null);
    }

    private ChecklistPrintResponse previewInternal(
            Long officeId,
            Long reportId,
            String checklistType,
            UserPrincipal principal
    ) {
        var report = reportRepository.findByIdAndOfficeId(reportId, officeId)
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        if (principal != null) {
            permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        }
        var selection = checklistSelection(report);
        var type = normalizeType(firstNonBlank(checklistType, selection.outputType()));
        var catalog = catalogService.get(catalogService.defaultConstructionCatalogCode(), report.siteId(), officeId);
        var dailyItems = REPORT_TYPE_CHECKLIST.equals(report.reportType())
                ? dailyItemsFromSelection(officeId, report, selection)
                : dailyItems(report);
        var overlays = collectOverlays(dailyItems);
        var documents = new ArrayList<ChecklistPrintDocumentResponse>();
        if (TYPE_TRADE.equals(type) || TYPE_ALL.equals(type)) {
            documents.addAll(tradeDocuments(catalog, overlays.tradeDocuments().values()));
        }
        if (TYPE_PHASE.equals(type) || TYPE_ALL.equals(type)) {
            documents.addAll(phaseDocuments(catalog, overlays.phaseDocuments().values()));
        }
        var checkedRows = documents.stream().mapToInt(ChecklistPrintDocumentResponse::checkedRowCount).sum();
        var response = new ChecklistPrintResponse(
                report.id(),
                nullToBlank(report.reportNo()),
                nullToBlank(report.title()),
                nullToBlank(report.reportType()),
                type,
                typeName(type),
                documents.size(),
                checkedRows,
                documents,
                ""
        );
        return new ChecklistPrintResponse(
                response.reportId(),
                response.reportNo(),
                response.reportTitle(),
                response.reportType(),
                response.checklistType(),
                response.checklistTypeName(),
                response.documentCount(),
                response.checkedRowCount(),
                response.documents(),
                renderHtml(report, response)
        );
    }

    private ChecklistSelection checklistSelection(InspectionReport report) {
        if (!REPORT_TYPE_CHECKLIST.equals(report.reportType())) {
            return ChecklistSelection.defaultSelection();
        }
        var raw = stepRepository.findByReportIdAndStepCode(report.id(), CHECKLIST_SOURCE_STEP)
                .map(step -> readNode(step.payloadJson().get(FIELD_CHECKLIST_SELECTION)))
                .orElseGet(objectMapper::createObjectNode);
        return new ChecklistSelection(
                normalizeType(text(raw, "outputType")),
                normalizeSelectionMode(text(raw, "selectionMode")),
                text(raw, "dateFrom"),
                text(raw, "dateTo"),
                selectedReportIds(raw.path("selectedReportIds")));
    }

    private JsonNode dailyItemsFromSelection(Long officeId, InspectionReport checklistReport, ChecklistSelection selection) {
        var merged = objectMapper.createObjectNode();
        var groups = merged.putArray("groups");
        var selectedIds = new HashSet<>(selection.selectedReportIds());
        for (var report : reportRepository.findByOfficeIdAndProjectIdOrderByUpdatedAtDesc(
                officeId,
                checklistReport.projectId())) {
            if (!isSourceDailyReport(checklistReport, report)) {
                continue;
            }
            if (!matchesSelection(report, selection, selectedIds)) {
                continue;
            }
            for (var group : dailyItems(report).path("groups")) {
                groups.add(group);
            }
        }
        return merged;
    }

    private boolean isSourceDailyReport(InspectionReport checklistReport, InspectionReport candidate) {
        return REPORT_TYPE_DAILY.equals(candidate.reportType())
                && candidate.status() != InspectionReportStatus.CANCELLED
                && Objects.equals(checklistReport.siteId(), candidate.siteId());
    }

    private boolean matchesSelection(
            InspectionReport report,
            ChecklistSelection selection,
            Set<Long> selectedReportIds
    ) {
        if (MODE_SELECTED_REPORTS.equals(selection.selectionMode())) {
            return selectedReportIds.contains(report.id());
        }
        if (MODE_DATE_RANGE.equals(selection.selectionMode())) {
            var inspectionDate = inspectionDate(report);
            if (inspectionDate == null) {
                return false;
            }
            var from = parseDate(selection.dateFrom());
            var to = parseDate(selection.dateTo());
            return (from == null || !inspectionDate.isBefore(from))
                    && (to == null || !inspectionDate.isAfter(to));
        }
        return true;
    }

    private LocalDate inspectionDate(InspectionReport report) {
        return stepRepository.findByReportIdAndStepCode(report.id(), BASIC_INFO_STEP)
                .map(step -> readNode(step.payloadJson()))
                .map(node -> parseDate(text(node, "inspectionDate")))
                .orElse(null);
    }

    private List<Long> selectedReportIds(JsonNode node) {
        var ids = new ArrayList<Long>();
        if (!node.isArray()) {
            return ids;
        }
        for (var item : node) {
            if (item.canConvertToLong()) {
                ids.add(item.asLong());
            } else {
                try {
                    ids.add(Long.parseLong(item.asText("")));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed client values. Required selection validation happens when saving/submitting.
                }
            }
        }
        return ids;
    }

    private JsonNode dailyItems(InspectionReport report) {
        return stepRepository.findByReportIdAndStepCode(report.id(), DAILY_LOG_STEP)
                .map(step -> readNode(step.payloadJson().get("dailyItems")))
                .filter(node -> node.path("groups").isArray())
                .orElseGet(objectMapper::createObjectNode);
    }

    private OverlayIndex collectOverlays(JsonNode dailyItems) {
        var tradeDocuments = new LinkedHashMap<String, DocumentOverlay>();
        var phaseDocuments = new LinkedHashMap<String, DocumentOverlay>();
        for (var group : dailyItems.path("groups")) {
            var groupType = text(group, "groupType");
            var phase = TYPE_PHASE.equalsIgnoreCase(groupType)
                    || (!text(group, "phaseCode").isBlank() && text(group, "tradeCode").isBlank());
            var entries = group.path("entries");
            if (!entries.isArray()) {
                continue;
            }
            var key = phase ? phaseDocumentKey(group) : tradeDocumentKey(group);
            var document = phase
                    ? phaseDocuments.computeIfAbsent(key, ignored -> DocumentOverlay.fromPhase(group))
                    : tradeDocuments.computeIfAbsent(key, ignored -> DocumentOverlay.fromTrade(group));
            for (var entry : entries) {
                var inspectionItemCode = text(entry, "inspectionItemCode");
                for (var row : entry.path("checklistRows")) {
                    var result = normalizeResult(text(row, "result"));
                    var referenceNote = text(row, "referenceNote");
                    var actionNote = text(row, "actionNote");
                    if (result.isBlank() && referenceNote.isBlank() && actionNote.isBlank()) {
                        continue;
                    }
                    var rowCode = firstNonBlank(text(row, "code"), inspectionItemCode);
                    if (inspectionItemCode.isBlank() || rowCode.isBlank()) {
                        continue;
                    }
                    document.rows().put(rowKey(inspectionItemCode, rowCode), new RowOverlay(result, referenceNote, actionNote));
                }
            }
        }
        return new OverlayIndex(tradeDocuments, phaseDocuments);
    }

    private List<ChecklistPrintDocumentResponse> tradeDocuments(JsonNode catalog, Iterable<DocumentOverlay> overlays) {
        var result = new ArrayList<ChecklistPrintDocumentResponse>();
        var mode = catalog.path("selectedSupervisionWorkMode").asText("");
        var modeName = catalog.path("selectedSupervisionWorkModeName").asText("");
        for (var overlay : overlays) {
            var tradeRef = findTradeRef(catalog.path("selectedSupervisionWorkModeCatalog").path("tradeRefs"), overlay.tradeCode());
            if (tradeRef == null) {
                continue;
            }
            var subTradeRef = findSubTradeRef(tradeRef.path("subTradeRefs"), overlay.subTradeCode());
            if (subTradeRef == null) {
                continue;
            }
            var rows = rowsForWorkCategories(catalog, subTradeRef.path("workCategories"), overlay.rows());
            if (rows.stream().noneMatch(ChecklistPrintRowResponse::checked)) {
                continue;
            }
            result.add(new ChecklistPrintDocumentResponse(
                    TYPE_TRADE,
                    "공종별 감리 체크리스트",
                    "",
                    mode,
                    modeName,
                    overlay.tradeGroupCode(),
                    overlay.tradeGroupName(),
                    overlay.tradeCode(),
                    overlay.tradeName(),
                    firstNonBlank(overlay.subTradeCode(), "NONE"),
                    displaySubTradeName(overlay.subTradeName()),
                    "",
                    "",
                    overlay.floorArea(),
                    overlay.location(),
                    rows.size(),
                    (int) rows.stream().filter(ChecklistPrintRowResponse::checked).count(),
                    rows
            ));
        }
        return result;
    }

    private List<ChecklistPrintDocumentResponse> phaseDocuments(JsonNode catalog, Iterable<DocumentOverlay> overlays) {
        var result = new ArrayList<ChecklistPrintDocumentResponse>();
        var mode = catalog.path("selectedSupervisionWorkMode").asText("");
        var modeName = catalog.path("selectedSupervisionWorkModeName").asText("");
        for (var overlay : overlays) {
            var phaseRef = findPhaseRef(catalog.path("selectedSupervisionWorkModeCatalog").path("phaseRefs"), overlay.phaseCode());
            if (phaseRef == null) {
                continue;
            }
            var rows = rowsForWorkCategories(catalog, phaseRef.path("workCategories"), overlay.rows());
            if (rows.stream().noneMatch(ChecklistPrintRowResponse::checked)) {
                continue;
            }
            result.add(new ChecklistPrintDocumentResponse(
                    TYPE_PHASE,
                    "단계별 감리업무 Check List",
                    "",
                    mode,
                    modeName,
                    "",
                    "",
                    "",
                    "",
                    "NONE",
                    "",
                    overlay.phaseCode(),
                    overlay.phaseName(),
                    overlay.floorArea(),
                    overlay.location(),
                    rows.size(),
                    (int) rows.stream().filter(ChecklistPrintRowResponse::checked).count(),
                    rows
            ));
        }
        return result;
    }

    private List<ChecklistPrintRowResponse> rowsForWorkCategories(
            JsonNode catalog,
            JsonNode workCategories,
            Map<String, RowOverlay> overlays
    ) {
        var rows = new ArrayList<ChecklistPrintRowResponse>();
        var atoms = catalog.path("canonicalAtoms");
        for (var category : workCategories) {
            var categoryCode = text(category, "code");
            var categoryName = text(category, "name");
            for (var processRef : category.path("processGroupRefs")) {
                var processCode = text(processRef, "code");
                var processName = text(atoms.path("processGroups").path(processCode), "name");
                for (var itemRef : processRef.path("itemRefs")) {
                    var itemCode = itemRef.asText("");
                    var item = atoms.path("inspectionItems").path(itemCode);
                    if (!item.isObject()) {
                        continue;
                    }
                    var itemName = text(item, "name");
                    rows.add(row(categoryCode, categoryName, processCode, processName, itemCode, itemName,
                            itemCode, itemName, text(item, "basis"), overlays.get(rowKey(itemCode, itemCode))));
                    for (var rowRef : item.path("rowRefs")) {
                        var rowCode = rowRef.asText("");
                        if (rowCode.isBlank() || rowCode.equals(itemCode)) {
                            continue;
                        }
                        var row = atoms.path("checklistRows").path(rowCode);
                        if (!row.isObject()) {
                            continue;
                        }
                        rows.add(row(categoryCode, categoryName, processCode, processName, itemCode, itemName,
                                rowCode, childRowLabel(text(row, "label")), text(row, "basis"), overlays.get(rowKey(itemCode, rowCode))));
                    }
                }
            }
        }
        return rows;
    }

    private ChecklistPrintRowResponse row(
            String categoryCode,
            String categoryName,
            String processCode,
            String processName,
            String itemCode,
            String itemName,
            String rowCode,
            String rowLabel,
            String basis,
            RowOverlay overlay
    ) {
        var checked = overlay != null && (!overlay.result().isBlank()
                || !overlay.referenceNote().isBlank()
                || !overlay.actionNote().isBlank());
        return new ChecklistPrintRowResponse(
                categoryCode,
                categoryName,
                processCode,
                processName,
                itemCode,
                itemName,
                rowCode,
                rowLabel,
                basis,
                overlay == null ? "" : overlay.referenceNote(),
                overlay == null ? "" : overlay.result(),
                overlay == null ? "" : overlay.actionNote(),
                checked
        );
    }

    private String renderHtml(InspectionReport report, ChecklistPrintResponse response) {
        var html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"utf-8\"><style>");
        html.append("body{font-family:'Malgun Gothic',Arial,sans-serif;margin:24px;color:#111827}");
        html.append(".doc{page-break-after:always;margin:0 auto 32px;max-width:920px}");
        html.append("h1{text-align:center;font-size:24px;margin:0 0 14px}");
        html.append("table{border-collapse:collapse;width:100%;table-layout:fixed}");
        html.append("th,td{border:1px solid #111827;padding:6px 7px;font-size:12px;vertical-align:middle;word-break:keep-all;overflow-wrap:anywhere}");
        html.append("th{background:#e5e7eb;text-align:center;font-weight:700}");
        html.append(".meta th{width:90px}.center{text-align:center}.small{font-size:11px}.sign td{height:34px}.muted{color:#6b7280}");
        html.append(".result{font-size:15px;font-weight:700}.unchecked{color:#d1d5db}.note{white-space:pre-wrap}");
        html.append("</style></head><body>");
        if (response.documents().isEmpty()) {
            html.append("<p>출력할 체크리스트가 없습니다. 감리일지에서 공종별 또는 단계별 항목을 먼저 체크하세요.</p>");
        }
        for (var document : response.documents()) {
            html.append("<section class=\"doc\">");
            if (TYPE_PHASE.equals(document.checklistType())) {
                renderPhaseDocument(html, report, document);
            } else {
                renderTradeDocument(html, document);
            }
            html.append("</section>");
        }
        html.append("</body></html>");
        return html.toString();
    }

    private void renderTradeDocument(StringBuilder html, ChecklistPrintDocumentResponse document) {
        html.append("<h1>공종별 감리 체크리스트</h1>");
        html.append("<table class=\"meta\"><tbody>");
        metaRow(html, "공종", document.tradeName(), "문서번호", document.documentNo());
        metaRow(html, "세부공종", blankAsDash(document.subTradeName()), "부위", blankAsDash(document.floorArea()));
        metaRow(html, "층", blankAsDash(document.floorArea()), "위치", blankAsDash(document.location()));
        html.append("</tbody></table>");
        html.append("<table><thead><tr>");
        html.append("<th style=\"width:72px\">구분</th><th style=\"width:88px\">세부공정</th><th>검사항목</th><th style=\"width:150px\">기준,참고사항</th>");
        html.append("<th style=\"width:54px\">적합</th><th style=\"width:54px\">부적합</th><th style=\"width:130px\">조치사항</th>");
        html.append("</tr></thead><tbody>");
        var rows = document.rows();
        for (var index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            html.append("<tr>");
            if (isFirstWorkCategory(rows, index)) {
                html.append("<td class=\"center\" rowspan=\"").append(workCategorySpan(rows, index)).append("\">")
                        .append(escape(row.workCategoryName())).append("</td>");
            }
            if (isFirstProcess(rows, index)) {
                html.append("<td rowspan=\"").append(processSpan(rows, index)).append("\">")
                        .append(escape(row.processName())).append("</td>");
            }
            html.append("<td>").append(escape(row.rowLabel()));
            html.append("</td>");
            html.append("<td class=\"note\">").append(escape(row.basis()));
            if (!row.referenceNote().isBlank()) {
                html.append("<div class=\"small muted note\">").append(escape(row.referenceNote())).append("</div>");
            }
            html.append("</td>");
            resultCells(html, row.result());
            html.append("<td class=\"note\">").append(escape(row.actionNote())).append("</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        renderTradeSignature(html);
    }

    private void renderPhaseDocument(StringBuilder html, InspectionReport report, ChecklistPrintDocumentResponse document) {
        html.append("<h1>단계별 감리업무 Check List</h1>");
        html.append("<table class=\"meta\"><tbody>");
        metaRow(html, "공사명", nullToBlank(report.title()), "문서번호", document.documentNo());
        metaRow(html, "건축주", "", "발행일시", "");
        metaRow(html, "공사단계", document.constructionPhaseName(), "업무구분", document.supervisionWorkModeName());
        html.append("</tbody></table>");
        html.append("<table><thead><tr>");
        html.append("<th style=\"width:118px\">검토항목</th><th>세부검토사항</th>");
        html.append("<th style=\"width:54px\">적합</th><th style=\"width:54px\">부적합</th><th style=\"width:130px\">조치사항</th>");
        html.append("</tr></thead><tbody>");
        var rows = document.rows();
        for (var index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            html.append("<tr>");
            if (isFirstProcess(rows, index)) {
                html.append("<td class=\"center\" rowspan=\"").append(processSpan(rows, index)).append("\">")
                        .append(escape(row.processName())).append("</td>");
            }
            html.append("<td>").append(escape(row.rowLabel()));
            if (!row.referenceNote().isBlank()) {
                html.append("<div class=\"small muted note\">").append(escape(row.referenceNote())).append("</div>");
            }
            html.append("</td>");
            resultCells(html, row.result());
            html.append("<td class=\"note\">").append(escape(row.actionNote())).append("</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        renderPhaseSignature(html);
    }

    private void renderTradeSignature(StringBuilder html) {
        html.append("<table class=\"sign\"><tbody>");
        html.append("<tr><th>시공자점검일</th><td class=\"center\">년&nbsp;&nbsp;&nbsp;월&nbsp;&nbsp;&nbsp;일</td><th>총괄 시공 책임자<br><span class=\"small\">(또는 현장관리인)</span></th><td>(인)</td></tr>");
        html.append("<tr><th></th><td></td><th>공종별 시공 관리자</th><td>(인)</td></tr>");
        html.append("<tr><th>감리자점검일</th><td class=\"center\">년&nbsp;&nbsp;&nbsp;월&nbsp;&nbsp;&nbsp;일</td><th>총괄 감리 책임자</th><td>(인)</td></tr>");
        html.append("<tr><th></th><td></td><th>건축사보<br><span class=\"small\">(공종별 감리 책임자)</span></th><td>(인)</td></tr>");
        html.append("<tr><th>첨부자료</th><td colspan=\"3\"></td></tr>");
        html.append("</tbody></table>");
    }

    private void renderPhaseSignature(StringBuilder html) {
        html.append("<p class=\"center\">- 상기와 같이 단계별 감리업무에 따른 검토 사항을 확인하여 제출합니다.</p>");
        html.append("<table class=\"sign\"><tbody>");
        html.append("<tr><td style=\"border:none\"></td><th>총괄 감리 책임자</th><td>(인)</td></tr>");
        html.append("<tr><td style=\"border:none\"></td><th>건축사보<br><span class=\"small\">(공종별 감리 책임자)</span></th><td>(인)</td></tr>");
        html.append("</tbody></table>");
    }

    private void resultCells(StringBuilder html, String result) {
        html.append("<td class=\"center result\">").append("COMPLIANT".equals(result) ? "○" : "").append("</td>");
        html.append("<td class=\"center result\">").append("NON_COMPLIANT".equals(result) ? "○" : "").append("</td>");
    }

    private void metaRow(StringBuilder html, String leftLabel, String leftValue, String rightLabel, String rightValue) {
        html.append("<tr><th>").append(escape(leftLabel)).append("</th><td>").append(escape(leftValue)).append("</td>");
        html.append("<th>").append(escape(rightLabel)).append("</th><td>").append(escape(rightValue)).append("</td></tr>");
    }

    private JsonNode findTradeRef(JsonNode refs, String tradeCode) {
        for (var ref : refs) {
            if (tradeCode.equals(text(ref, "tradeCode"))) {
                return ref;
            }
        }
        return null;
    }

    private JsonNode findSubTradeRef(JsonNode refs, String subTradeCode) {
        var expected = firstNonBlank(subTradeCode, "NONE");
        for (var ref : refs) {
            if (expected.equals(firstNonBlank(text(ref, "subTradeCode"), "NONE"))) {
                return ref;
            }
        }
        return null;
    }

    private JsonNode findPhaseRef(JsonNode refs, String phaseCode) {
        for (var ref : refs) {
            if (phaseCode.equals(text(ref, "phaseCode"))) {
                return ref;
            }
        }
        return null;
    }

    private String tradeDocumentKey(JsonNode group) {
        return String.join("|",
                text(group, "tradeCode"),
                firstNonBlank(text(group, "subTradeCode"), "NONE"),
                text(group, "floor"),
                text(group, "location"));
    }

    private String phaseDocumentKey(JsonNode group) {
        return String.join("|",
                text(group, "phaseCode"),
                text(group, "floor"),
                text(group, "location"));
    }

    private String rowKey(String inspectionItemCode, String rowCode) {
        return inspectionItemCode + "::" + rowCode;
    }

    private String normalizeType(String value) {
        var normalized = value == null ? TYPE_ALL : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case TYPE_TRADE, TYPE_PHASE, TYPE_ALL -> normalized;
            default -> TYPE_ALL;
        };
    }

    private String normalizeSelectionMode(String value) {
        var normalized = value == null ? MODE_ALL_SITE : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case MODE_DATE_RANGE, MODE_SELECTED_REPORTS -> normalized;
            default -> MODE_ALL_SITE;
        };
    }

    private String typeName(String type) {
        return switch (type) {
            case TYPE_TRADE -> "공종별 체크리스트";
            case TYPE_PHASE -> "단계별 체크리스트";
            default -> "전체 체크리스트";
        };
    }

    private String normalizeResult(String value) {
        var normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("COMPLIANT".equals(normalized) || "NON_COMPLIANT".equals(normalized)) {
            return normalized;
        }
        return "";
    }

    private String childRowLabel(String label) {
        var value = nullToBlank(label).trim();
        if (value.isBlank() || value.startsWith("-") || value.startsWith("·")) {
            return value;
        }
        return "- " + value;
    }

    private boolean isFirstWorkCategory(List<ChecklistPrintRowResponse> rows, int index) {
        return index == 0 || !Objects.equals(
                rows.get(index - 1).workCategoryCode(),
                rows.get(index).workCategoryCode());
    }

    private int workCategorySpan(List<ChecklistPrintRowResponse> rows, int startIndex) {
        var current = rows.get(startIndex);
        var span = 0;
        for (var index = startIndex; index < rows.size(); index++) {
            var candidate = rows.get(index);
            if (!Objects.equals(current.workCategoryCode(), candidate.workCategoryCode())) {
                break;
            }
            span++;
        }
        return Math.max(span, 1);
    }

    private boolean isFirstProcess(List<ChecklistPrintRowResponse> rows, int index) {
        return index == 0
                || !Objects.equals(rows.get(index - 1).workCategoryCode(), rows.get(index).workCategoryCode())
                || !Objects.equals(rows.get(index - 1).processCode(), rows.get(index).processCode());
    }

    private int processSpan(List<ChecklistPrintRowResponse> rows, int startIndex) {
        var current = rows.get(startIndex);
        var span = 0;
        for (var index = startIndex; index < rows.size(); index++) {
            var candidate = rows.get(index);
            if (!Objects.equals(current.workCategoryCode(), candidate.workCategoryCode())
                    || !Objects.equals(current.processCode(), candidate.processCode())) {
                break;
            }
            span++;
        }
        return Math.max(span, 1);
    }

    private JsonNode readNode(Object value) {
        if (value == null) {
            return objectMapper.createObjectNode();
        }
        try {
            if (value instanceof String text) {
                return text.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(text);
            }
            return objectMapper.valueToTree(value);
        } catch (RuntimeException | java.io.IOException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String text(JsonNode node, String key) {
        if (node == null) {
            return "";
        }
        return node.path(key).asText("");
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? nullToBlank(second).trim() : first.trim();
    }

    private String displaySubTradeName(String value) {
        var text = nullToBlank(value);
        return text.isBlank() || "NONE".equalsIgnoreCase(text) || "없음".equals(text) ? "-" : text;
    }

    private String blankAsDash(String value) {
        var text = nullToBlank(value);
        return text.isBlank() ? "-" : text;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        return nullToBlank(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record OverlayIndex(
            Map<String, DocumentOverlay> tradeDocuments,
            Map<String, DocumentOverlay> phaseDocuments
    ) {
    }

    private record DocumentOverlay(
            String tradeGroupCode,
            String tradeGroupName,
            String tradeCode,
            String tradeName,
            String subTradeCode,
            String subTradeName,
            String phaseCode,
            String phaseName,
            String floorArea,
            String location,
            Map<String, RowOverlay> rows
    ) {
        static DocumentOverlay fromTrade(JsonNode group) {
            return new DocumentOverlay(
                    group.path("tradeGroupCode").asText(""),
                    group.path("tradeGroupName").asText(""),
                    group.path("tradeCode").asText(""),
                    group.path("tradeName").asText(""),
                    group.path("subTradeCode").asText("NONE"),
                    group.path("subTradeName").asText(""),
                    "",
                    "",
                    group.path("floor").asText(""),
                    group.path("location").asText(""),
                    new LinkedHashMap<>());
        }

        static DocumentOverlay fromPhase(JsonNode group) {
            return new DocumentOverlay(
                    "",
                    "",
                    "",
                    "",
                    "NONE",
                    "",
                    group.path("phaseCode").asText(""),
                    group.path("phaseName").asText(""),
                    group.path("floor").asText(""),
                    group.path("location").asText(""),
                    new LinkedHashMap<>());
        }
    }

    private record RowOverlay(
            String result,
            String referenceNote,
            String actionNote
    ) {
    }

    private record ChecklistSelection(
            String outputType,
            String selectionMode,
            String dateFrom,
            String dateTo,
            List<Long> selectedReportIds
    ) {
        static ChecklistSelection defaultSelection() {
            return new ChecklistSelection(TYPE_ALL, MODE_ALL_SITE, "", "", List.of());
        }
    }
}

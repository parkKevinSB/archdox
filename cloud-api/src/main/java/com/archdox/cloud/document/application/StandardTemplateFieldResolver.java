package com.archdox.cloud.document.application;

import static com.archdox.cloud.document.application.DocumentSnapshotPath.readPath;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StandardTemplateFieldResolver {
    public Map<String, Object> resolve(Map<String, Object> snapshot) {
        var fields = new LinkedHashMap<String, Object>();
        var reportType = readFirst(snapshot, "report.reportType");
        var inspectionDate = readFirst(
                snapshot,
                "steps.BASIC_INFO.payload.inspectionDate",
                "steps.DAILY_LOG.payload.inspectionDate",
                "steps.SAFETY_CHECK.payload.inspectionDate",
                "steps.CHECKLIST.payload.inspectionDate",
                "steps.DEMOLITION_SAFETY_CHECK.payload.inspectionDate",
                "steps.DEMOLITION_DAILY_LOG.payload.inspectionDate");

        put(fields, "documentTitle", titleFor(reportType, readFirst(snapshot, "report.title")));
        put(fields, "reportTitle", readFirst(snapshot, "report.title"));
        put(fields, "reportNo", readFirst(snapshot, "report.reportNo"));
        put(fields, "serialNo", readFirst(snapshot, "report.reportNo"));
        put(fields, "reportType", reportType);

        put(fields, "projectName", readFirst(snapshot, "project.name"));
        put(fields, "constructionName", readFirst(snapshot, "project.name"));
        put(fields, "constructionProjectName", readFirst(snapshot, "project.name"));
        put(fields, "workName", readFirst(snapshot, "project.name"));
        put(fields, "projectAddress", readFirst(snapshot, "project.address"));
        put(fields, "siteName", readFirst(snapshot, "site.name", "project.name"));
        put(fields, "siteCode", readFirst(snapshot, "site.siteCode"));
        put(fields, "siteAddress", readFirst(snapshot, "site.address", "project.address"));
        put(fields, "siteType", readFirst(snapshot, "site.siteType"));
        put(fields, "buildingType", readFirst(snapshot, "project.buildingType"));
        put(fields, "lotNumber", readFirst(snapshot, "steps.BASIC_INFO.payload.lotNumber"));

        put(fields, "permitNumber", readFirst(snapshot, "steps.BASIC_INFO.payload.permitNumber"));
        put(fields, "permitDate", readFirst(snapshot, "steps.BASIC_INFO.payload.permitDate"));
        put(fields, "constructionStartDate", readFirst(snapshot, "project.startDate", "site.startDate"));
        put(fields, "constructionEndDate", readFirst(snapshot, "project.endDate", "site.endDate"));
        put(fields, "supervisionStartDate", readFirst(snapshot, "steps.BASIC_INFO.payload.supervisionStartDate", "project.startDate"));
        put(fields, "supervisionEndDate", readFirst(snapshot, "steps.BASIC_INFO.payload.supervisionEndDate", "project.endDate"));

        put(fields, "inspectionDate", inspectionDate);
        put(fields, "safetyInspectionDate", inspectionDate);
        putDateParts(fields, inspectionDate);
        put(fields, "weather", readFirst(snapshot, "steps.BASIC_INFO.payload.weather", "steps.DAILY_LOG.payload.weather"));
        put(fields, "inspectionLocation", readFirst(
                snapshot,
                "steps.BASIC_INFO.payload.location",
                "steps.SAFETY_CHECK.payload.location",
                "steps.CHECKLIST.payload.location",
                "steps.DEMOLITION_SAFETY_CHECK.payload.location",
                "steps.DEMOLITION_DAILY_LOG.payload.location",
                "site.name",
                "site.address"));

        put(fields, "chiefSupervisorName", readFirst(
                snapshot,
                "steps.BASIC_INFO.payload.chiefSupervisorName",
                "steps.BASIC_INFO.payload.supervisorName",
                "steps.BASIC_INFO.payload.inspectorName"));
        put(fields, "supervisorName", readFirst(
                snapshot,
                "steps.BASIC_INFO.payload.supervisorName",
                "steps.BASIC_INFO.payload.inspectorName"));
        put(fields, "inspectorName", readFirst(snapshot, "steps.BASIC_INFO.payload.inspectorName"));
        put(fields, "architectAssistantName", readFirst(
                snapshot,
                "steps.BASIC_INFO.payload.architectAssistantName",
                "steps.BASIC_INFO.payload.assistantSupervisorName"));
        put(fields, "assistantArchitectName", fields.get("architectAssistantName"));
        put(fields, "assistantSupervisorName", readFirst(snapshot, "steps.BASIC_INFO.payload.assistantSupervisorName"));
        put(fields, "demolitionWorkerName", readFirst(
                snapshot,
                "steps.BASIC_INFO.payload.demolitionWorkerName",
                "steps.DEMOLITION_SAFETY_CHECK.payload.demolitionWorkerName"));
        put(fields, "supervisorOfficeName", readFirst(snapshot, "steps.BASIC_INFO.payload.supervisorOfficeName"));
        put(fields, "contractorName", readFirst(snapshot, "steps.BASIC_INFO.payload.contractorName"));
        put(fields, "serviceName", readFirst(snapshot, "steps.BASIC_INFO.payload.serviceName"));
        put(fields, "reportDate", readFirst(snapshot, "steps.BASIC_INFO.payload.reportDate", "steps.REMARKS.payload.reportDate"));

        put(fields, "constructionTrade", readFirst(
                snapshot,
                "steps.DAILY_LOG.payload.constructionTrade",
                "steps.DAILY_LOG.payload.trade",
                "steps.WORK_STATUS.payload.constructionTrade"));
        put(fields, "detailedProcess", readFirst(
                snapshot,
                "steps.DAILY_LOG.payload.detailedProcess",
                "steps.DAILY_LOG.payload.process",
                "steps.WORK_STATUS.payload.detailedProcess"));
        put(fields, "floor", readFirst(snapshot, "steps.DAILY_LOG.payload.floor", "steps.WORK_STATUS.payload.floor"));
        put(fields, "workDescription", readFirst(
                snapshot,
                "steps.DAILY_LOG.payload.workDescription",
                "steps.DEMOLITION_DAILY_LOG.payload.workDescription",
                "steps.WORK_STATUS.payload.workDescription"));
        put(fields, "inspectionItem", readFirst(
                snapshot,
                "steps.DAILY_LOG.payload.inspectionItemName",
                "steps.DAILY_LOG.payload.inspectionItem",
                "steps.DAILY_LOG.payload.supervisionItem",
                "steps.CHECKLIST.payload.inspectionItemName"));
        put(fields, "supervisionItem", readFirst(
                snapshot,
                "steps.DAILY_LOG.payload.supervisionItem",
                "steps.DAILY_LOG.payload.inspectionItemName",
                "steps.CHECKLIST.payload.supervisionItem"));
        put(fields, "supervisionFocus", readFirst(
                snapshot,
                "steps.DEMOLITION_DAILY_LOG.payload.supervisionFocus",
                "steps.DAILY_LOG.payload.supervisionFocus"));
        put(fields, "supervisionContent", readFirst(
                snapshot,
                "steps.DAILY_LOG.payload.supervisionContent",
                "steps.DEMOLITION_DAILY_LOG.payload.supervisionContent",
                "steps.CHECKLIST.payload.checklistSummary"));
        put(fields, "specialNotes", readFirst(
                snapshot,
                "steps.DAILY_LOG.payload.specialNotes",
                "steps.DEMOLITION_DAILY_LOG.payload.specialNotes",
                "steps.NOTES.payload.specialNotes"));
        put(fields, "issueAndAction", readFirst(
                snapshot,
                "steps.DAILY_LOG.payload.issueAndAction",
                "steps.DAILY_LOG.payload.issueAndActionResult",
                "steps.DEMOLITION_DAILY_LOG.payload.issueAndAction",
                "steps.ISSUES.payload.issueAndAction"));
        put(fields, "correctionResults", readFirst(
                snapshot,
                "steps.DAILY_LOG.payload.correctionResults",
                "steps.DAILY_LOG.payload.issueAndAction",
                "steps.DEMOLITION_DAILY_LOG.payload.issueAndAction",
                "steps.ISSUES.payload.issueAndAction"));
        put(fields, "correctiveAction", readFirst(
                snapshot,
                "steps.SAFETY_CHECK.payload.correctiveAction",
                "steps.DEMOLITION_SAFETY_CHECK.payload.correctiveAction",
                "steps.ISSUES.payload.correctiveAction"));
        put(fields, "relationEngineerOpinion", readFirst(snapshot, "steps.REMARKS.payload.relationEngineerOpinion"));
        put(fields, "comprehensiveOpinion", readFirst(
                snapshot,
                "steps.REMARKS.payload.comprehensiveOpinion",
                "steps.SUPERVISOR_DEPLOYMENT.payload.comprehensiveOpinion"));
        put(fields, "checklistSummary", readFirst(snapshot, "steps.CHECKLIST.payload.checklistSummary"));
        put(fields, "issueCount", readFirst(snapshot, "steps.CHECKLIST.payload.issueCount", "steps.ISSUES.payload.issueCount"));

        put(fields, "safetyCheckStage", readFirst(
                snapshot,
                "steps.BASIC_INFO.payload.stage",
                "steps.DEMOLITION_SAFETY_CHECK.payload.stage"));
        put(fields, "demolitionWorkStage", readFirst(
                snapshot,
                "steps.BASIC_INFO.payload.stage",
                "steps.DEMOLITION_SAFETY_CHECK.payload.stage",
                "steps.DEMOLITION_DAILY_LOG.payload.stage"));
        put(fields, "inspectionCriteria", readFirst(snapshot, "steps.DEMOLITION_SAFETY_CHECK.payload.inspectionCriteria"));
        put(fields, "inspectionResult", readFirst(snapshot, "steps.DEMOLITION_SAFETY_CHECK.payload.inspectionResult"));
        put(fields, "safetyChecklistItems", compactList(readPath(snapshot, "checklistAnswers").orElse(List.of())));
        put(fields, "checklistPhotoSummary", compactList(readPath(snapshot, "checklistPhotos").orElse(List.of())));
        return fields;
    }

    private void putDateParts(Map<String, Object> fields, String dateText) {
        var date = parseDate(dateText);
        if (date == null) {
            put(fields, "inspectionYear", "");
            put(fields, "inspectionMonth", "");
            put(fields, "inspectionDay", "");
            put(fields, "inspectionDayOfWeek", "");
            return;
        }
        put(fields, "inspectionYear", String.valueOf(date.getYear()));
        put(fields, "inspectionMonth", String.valueOf(date.getMonthValue()));
        put(fields, "inspectionDay", String.valueOf(date.getDayOfMonth()));
        put(fields, "inspectionDayOfWeek", koreanDayOfWeek(date.getDayOfWeek()));
    }

    private String titleFor(String reportType, String fallbackTitle) {
        if (fallbackTitle != null && !fallbackTitle.isBlank()) {
            return fallbackTitle;
        }
        var normalized = reportType == null ? "" : reportType.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "CONSTRUCTION_SUPERVISION_REPORT", "SUPERVISION_REPORT" -> "\uAC10\uB9AC\uBCF4\uACE0\uC11C";
            case "DAILY_SUPERVISION", "CONSTRUCTION_DAILY_LOG", "CONSTRUCTION_DAILY_SUPERVISION_LOG" -> "\uACF5\uC0AC\uAC10\uB9AC\uC77C\uC9C0";
            case "DEMOLITION_SAFETY_CHECK", "DEMOLITION_SAFETY_CHECKLIST" -> "\uD574\uCCB4\uACF5\uC0AC \uC548\uC804\uC810\uAC80\uD45C";
            case "DEMOLITION_DAILY_SUPERVISION", "DEMOLITION_DAILY_LOG", "DEMOLITION_DAILY_SUPERVISION_LOG" -> "\uD574\uCCB4 \uACF5\uC0AC\uAC10\uB9AC\uC77C\uC9C0";
            case "DEMOLITION_COMPLETION_REPORT" -> "\uAC74\uCD95\uBB3C \uD574\uCCB4\uAC10\uB9AC\uC644\uB8CC \uBCF4\uACE0\uC11C";
            default -> "";
        };
    }

    private String readFirst(Map<String, Object> snapshot, String... paths) {
        for (String path : paths) {
            var value = readPath(snapshot, path).orElse(null);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private void put(Map<String, Object> fields, String key, Object value) {
        fields.put(key, value == null ? "" : value);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String koreanDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "\uC6D4";
            case TUESDAY -> "\uD654";
            case WEDNESDAY -> "\uC218";
            case THURSDAY -> "\uBAA9";
            case FRIDAY -> "\uAE08";
            case SATURDAY -> "\uD1A0";
            case SUNDAY -> "\uC77C";
        };
    }

    private String compactList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        var rows = new ArrayList<String>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                var label = firstNonBlank(map, "label", "name", "itemName", "title", "checklistItemKey");
                var result = firstNonBlank(map, "result", "status", "answer", "value");
                var note = firstNonBlank(map, "note", "comment", "memo", "description");
                var photoCount = firstNonBlank(map, "photoCount");
                var photoSummary = photoCount == null || photoCount.isBlank() || "0".equals(photoCount)
                        ? ""
                        : "Photos: " + photoCount;
                var row = String.join(" / ", List.of(label, result, note).stream()
                        .filter(text -> text != null && !text.isBlank())
                        .toList());
                row = String.join(" / ", List.of(row, photoSummary).stream()
                        .filter(text -> text != null && !text.isBlank())
                        .toList());
                if (!row.isBlank()) {
                    rows.add(row);
                }
            }
        }
        return String.join("\n", rows);
    }

    private String firstNonBlank(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            var value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "";
    }
}

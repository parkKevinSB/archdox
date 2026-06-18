package com.archdox.cloud.inspection.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DailySupervisionContentFormatter {
    public static final String DOCUMENT_NARRATIVE_TEXT_KEY = "documentNarrativeText";

    private DailySupervisionContentFormatter() {
    }

    public static String formatEntry(Map<String, Object> entry) {
        return formatEntry(entry, true);
    }

    public static String formatEntry(Map<String, Object> entry, boolean includeTitle) {
        if (entry == null || entry.isEmpty()) {
            return "";
        }
        var title = text(entry.get("inspectionItemName"));
        var parentRow = parentRow(entry, title);
        var parentInspected = isInspectedResult(text(parentRow.get("result")));
        var titleLine = includeTitle ? formatTitle(title, parentRow) : "";
        var rows = new ArrayList<String>();
        for (Object rowValue : listValue(entry.get("checklistRows"))) {
            var row = mapValue(rowValue);
            if (isSameRow(row, parentRow)) {
                continue;
            }
            var rowContent = formatRow(row);
            if (!rowContent.isBlank()) {
                rows.add("- " + rowContent);
            }
        }
        if (rows.isEmpty() && !parentInspected) {
            return "";
        }
        if (titleLine.isBlank() && rows.isEmpty()) {
            return "";
        }
        if (!titleLine.isBlank()) {
            rows.add(0, titleLine);
        }
        return String.join("\n", rows);
    }

    public static String formatEntry(JsonNode entry) {
        return formatEntry(entry, true);
    }

    public static String formatEntry(JsonNode entry, boolean includeTitle) {
        if (entry == null || entry.isMissingNode() || entry.isNull()) {
            return "";
        }
        var rowsNode = entry.path("checklistRows");
        if (!rowsNode.isArray()) {
            return "";
        }
        var title = text(entry, "inspectionItemName");
        var parentRow = parentRow(entry, title);
        var parentInspected = parentRow != null && isInspectedResult(text(parentRow, "result"));
        var titleLine = includeTitle ? formatTitle(title, parentRow) : "";
        var rows = new ArrayList<String>();
        rowsNode.forEach(row -> {
            if (isSameRow(row, parentRow)) {
                return;
            }
            var rowContent = formatRow(row);
            if (!rowContent.isBlank()) {
                rows.add("- " + rowContent);
            }
        });
        if (rows.isEmpty() && !parentInspected) {
            return "";
        }
        if (titleLine.isBlank() && rows.isEmpty()) {
            return "";
        }
        if (!titleLine.isBlank()) {
            rows.add(0, titleLine);
        }
        return String.join("\n", rows);
    }

    public static String formatDocumentEntry(Map<String, Object> entry) {
        return formatDocumentEntry(entry, true);
    }

    public static String formatDocumentEntry(Map<String, Object> entry, boolean includeTitle) {
        var documentText = text(entry == null ? null : entry.get(DOCUMENT_NARRATIVE_TEXT_KEY));
        if (!documentText.isBlank()) {
            return documentText;
        }
        return formatEntry(entry, includeTitle);
    }

    public static String formatDocumentEntry(JsonNode entry) {
        return formatDocumentEntry(entry, true);
    }

    public static String formatDocumentEntry(JsonNode entry, boolean includeTitle) {
        if (entry == null || entry.isMissingNode() || entry.isNull()) {
            return "";
        }
        var documentText = text(entry, DOCUMENT_NARRATIVE_TEXT_KEY);
        if (!documentText.isBlank()) {
            return documentText;
        }
        return formatEntry(entry, includeTitle);
    }

    public static boolean hasInspectedRow(Map<String, Object> entry) {
        return inspectedRowCount(entry) > 0;
    }

    public static int inspectedRowCount(Map<String, Object> entry) {
        if (entry == null) {
            return 0;
        }
        var inspected = 0;
        for (Object rowValue : listValue(entry.get("checklistRows"))) {
            if (isInspectedResult(text(mapValue(rowValue).get("result")))) {
                inspected++;
            }
        }
        return inspected;
    }

    public static boolean isKnownResult(String result) {
        var normalized = normalizeResult(result);
        return "COMPLIANT".equals(normalized)
                || "NON_COMPLIANT".equals(normalized)
                || "NOT_APPLICABLE".equals(normalized);
    }

    public static boolean isInspectedResult(String result) {
        var normalized = normalizeResult(result);
        return "COMPLIANT".equals(normalized) || "NON_COMPLIANT".equals(normalized);
    }

    public static String formatRowContent(Map<String, Object> row) {
        return formatRow(mapValue(row));
    }

    public static String formatRowContent(JsonNode row) {
        return formatRow(row);
    }

    private static String formatRow(Map<String, Object> row) {
        var result = normalizeResult(text(row.get("result")));
        if (!isInspectedResult(result)) {
            return "";
        }
        var label = firstNonBlank(text(row.get("label")), text(row.get("code")));
        if (label.isBlank()) {
            return "";
        }
        var parts = new ArrayList<String>();
        parts.add(label);
        parts.add(resultLabel(result));
        var referenceNote = text(row.get("referenceNote"));
        if (!referenceNote.isBlank()) {
            parts.add("기준·참고: " + referenceNote);
        }
        var actionNote = text(row.get("actionNote"));
        if (!actionNote.isBlank()) {
            parts.add("조치사항: " + actionNote);
        }
        return String.join(" / ", parts);
    }

    private static String formatTitle(String title, Map<String, Object> parentRow) {
        if (title.isBlank()) {
            return "";
        }
        var result = normalizeResult(text(parentRow.get("result")));
        if (!isInspectedResult(result)) {
            return title;
        }
        var parts = new ArrayList<String>();
        parts.add(title);
        parts.add(resultLabel(result));
        var referenceNote = text(parentRow.get("referenceNote"));
        if (!referenceNote.isBlank()) {
            parts.add("기준·참고: " + referenceNote);
        }
        var actionNote = text(parentRow.get("actionNote"));
        if (!actionNote.isBlank()) {
            parts.add("조치사항: " + actionNote);
        }
        return String.join(" / ", parts);
    }

    private static String formatTitle(String title, JsonNode parentRow) {
        if (title.isBlank()) {
            return "";
        }
        if (parentRow == null || parentRow.isMissingNode() || parentRow.isNull()) {
            return title;
        }
        var result = normalizeResult(text(parentRow, "result"));
        if (!isInspectedResult(result)) {
            return title;
        }
        var parts = new ArrayList<String>();
        parts.add(title);
        parts.add(resultLabel(result));
        var referenceNote = text(parentRow, "referenceNote");
        if (!referenceNote.isBlank()) {
            parts.add("기준·참고: " + referenceNote);
        }
        var actionNote = text(parentRow, "actionNote");
        if (!actionNote.isBlank()) {
            parts.add("조치사항: " + actionNote);
        }
        return String.join(" / ", parts);
    }

    private static Map<String, Object> parentRow(Map<String, Object> entry, String title) {
        var itemCode = text(entry.get("inspectionItemCode"));
        for (Object rowValue : listValue(entry.get("checklistRows"))) {
            var row = mapValue(rowValue);
            if (isParentRow(row, itemCode, title)) {
                return row;
            }
        }
        return Map.of();
    }

    private static JsonNode parentRow(JsonNode entry, String title) {
        var itemCode = text(entry, "inspectionItemCode");
        for (var row : entry.path("checklistRows")) {
            if (isParentRow(row, itemCode, title)) {
                return row;
            }
        }
        return null;
    }

    private static boolean isParentRow(Map<String, Object> row, String itemCode, String title) {
        if (row.isEmpty()) {
            return false;
        }
        var code = text(row.get("code"));
        var label = text(row.get("label"));
        return (!itemCode.isBlank() && itemCode.equals(code))
                || (!title.isBlank() && title.equals(label));
    }

    private static boolean isParentRow(JsonNode row, String itemCode, String title) {
        var code = text(row, "code");
        var label = text(row, "label");
        return (!itemCode.isBlank() && itemCode.equals(code))
                || (!title.isBlank() && title.equals(label));
    }

    private static boolean isSameRow(Map<String, Object> row, Map<String, Object> target) {
        return !target.isEmpty() && row == target;
    }

    private static boolean isSameRow(JsonNode row, JsonNode target) {
        return target != null && row == target;
    }

    private static String formatRow(JsonNode row) {
        var result = normalizeResult(text(row, "result"));
        if (!isInspectedResult(result)) {
            return "";
        }
        var label = firstNonBlank(text(row, "label"), text(row, "code"));
        if (label.isBlank()) {
            return "";
        }
        var parts = new ArrayList<String>();
        parts.add(label);
        parts.add(resultLabel(result));
        var referenceNote = text(row, "referenceNote");
        if (!referenceNote.isBlank()) {
            parts.add("기준·참고: " + referenceNote);
        }
        var actionNote = text(row, "actionNote");
        if (!actionNote.isBlank()) {
            parts.add("조치사항: " + actionNote);
        }
        return String.join(" / ", parts);
    }

    private static String resultLabel(String result) {
        return switch (normalizeResult(result)) {
            case "COMPLIANT" -> "적합";
            case "NON_COMPLIANT" -> "부적합";
            default -> "";
        };
    }

    private static String normalizeResult(String result) {
        return result == null ? "" : result.trim().toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String text(JsonNode node, String key) {
        var value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}

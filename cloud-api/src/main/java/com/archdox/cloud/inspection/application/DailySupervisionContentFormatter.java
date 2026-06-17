package com.archdox.cloud.inspection.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DailySupervisionContentFormatter {
    private DailySupervisionContentFormatter() {
    }

    public static String formatEntry(Map<String, Object> entry) {
        return formatEntry(entry, true);
    }

    public static String formatEntry(Map<String, Object> entry, boolean includeTitle) {
        if (entry == null || entry.isEmpty()) {
            return "";
        }
        var rows = new ArrayList<String>();
        for (Object rowValue : listValue(entry.get("checklistRows"))) {
            var rowContent = formatRow(mapValue(rowValue));
            if (!rowContent.isBlank()) {
                rows.add("- " + rowContent);
            }
        }
        if (rows.isEmpty()) {
            return "";
        }
        var title = text(entry.get("inspectionItemName"));
        if (includeTitle && !title.isBlank()) {
            rows.add(0, title);
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
        var rows = new ArrayList<String>();
        rowsNode.forEach(row -> {
            var rowContent = formatRow(row);
            if (!rowContent.isBlank()) {
                rows.add("- " + rowContent);
            }
        });
        if (rows.isEmpty()) {
            return "";
        }
        var title = text(entry, "inspectionItemName");
        if (includeTitle && !title.isBlank()) {
            rows.add(0, title);
        }
        return String.join("\n", rows);
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

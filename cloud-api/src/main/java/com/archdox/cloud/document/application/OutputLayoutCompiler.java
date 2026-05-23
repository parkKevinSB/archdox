package com.archdox.cloud.document.application;

import static com.archdox.cloud.document.application.DocumentSnapshotPath.firstString;
import static com.archdox.cloud.document.application.DocumentSnapshotPath.intValue;
import static com.archdox.cloud.document.application.DocumentSnapshotPath.listValue;
import static com.archdox.cloud.document.application.DocumentSnapshotPath.normalizeCode;
import static com.archdox.cloud.document.application.DocumentSnapshotPath.readPath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OutputLayoutCompiler {
    public OutputLayoutBinding compile(Map<String, Object> outputLayout, Map<String, Object> snapshot) {
        var rawSections = outputLayout == null ? null : outputLayout.get("sections");
        if (!(rawSections instanceof List<?> sectionList) || sectionList.isEmpty()) {
            return new OutputLayoutBinding(Map.of(), Map.of());
        }
        var sections = new LinkedHashMap<String, Object>();
        var fields = new LinkedHashMap<String, Object>();
        for (Object rawSection : sectionList) {
            if (!(rawSection instanceof Map<?, ?> section)) {
                continue;
            }
            var key = firstString(section, "key", "id", "name");
            if (key == null) {
                continue;
            }
            var rendered = renderLayoutSection(section, snapshot);
            if (rendered == null) {
                continue;
            }
            sections.put(key, rendered);
            fields.put(key, rendered.getOrDefault("text", ""));
        }
        return new OutputLayoutBinding(sections, fields);
    }

    private Map<String, Object> renderLayoutSection(Map<?, ?> section, Map<String, Object> snapshot) {
        var type = normalizeCode(firstString(section, "type", "sectionType"));
        var title = firstString(section, "title", "label");
        return switch (type) {
            case "PHOTO_SUMMARY", "PHOTO_LIST", "PHOTO_TABLE" ->
                    renderListSection(section, title, listValue(snapshot.get("photos")), defaultPhotoFields());
            case "CHECKLIST_SUMMARY", "CHECKLIST_LIST", "CHECKLIST_TABLE" ->
                    renderListSection(section, title, listValue(snapshot.get("checklistAnswers")), defaultChecklistFields());
            case "VALUE", "FIELD", "SNAPSHOT_VALUE" -> renderValueSection(section, title, snapshot);
            case "TEXT" -> renderTextSection(title, firstString(section, "text", "value"));
            default -> null;
        };
    }

    private Map<String, Object> renderListSection(
            Map<?, ?> section,
            String title,
            List<?> items,
            List<Map<String, String>> defaultFields
    ) {
        var fields = layoutFields(section.get("fields"), defaultFields);
        var lines = new ArrayList<String>();
        if (!Boolean.FALSE.equals(section.get("includeTitle")) && title != null) {
            lines.add(title);
        }
        if (items.isEmpty()) {
            lines.add(firstString(section, "emptyText", "emptyMessage") == null
                    ? "No items."
                    : firstString(section, "emptyText", "emptyMessage"));
        } else {
            for (int i = 0; i < items.size(); i++) {
                lines.add((i + 1) + ". " + renderLayoutItem(items.get(i), fields));
            }
        }
        var renderedItems = items.stream()
                .map(item -> renderLayoutItem(item, fields))
                .toList();
        var rendered = new LinkedHashMap<String, Object>();
        var type = normalizeCode(firstString(section, "type", "sectionType"));
        rendered.put("type", type);
        rendered.put("title", title == null ? "" : title);
        rendered.put("text", String.join("\n", lines));
        rendered.put("itemCount", items.size());
        rendered.put("items", renderedItems);
        copyLayoutOptions(section, rendered);
        if (type.startsWith("PHOTO_") || type.startsWith("CHECKLIST_")) {
            rendered.put("fields", fields);
        }
        if (type.startsWith("PHOTO_")) {
            rendered.put("photosPerRow", intValue(section.get("photosPerRow"), 1));
            var imageSize = firstString(section, "imageSize", "layoutSize", "photoLayoutSize");
            if (imageSize != null) {
                rendered.put("imageSize", normalizeCode(imageSize));
            }
        }
        return rendered;
    }

    private void copyLayoutOptions(Map<?, ?> section, Map<String, Object> rendered) {
        copyIfPresent(section, rendered, "includeTitle");
        copyIfPresent(section, rendered, "emptyText");
        copyIfPresent(section, rendered, "emptyMessage");
        copyIfPresent(section, rendered, "tableStyle");
        copyIfPresent(section, rendered, "tableWidth");
        copyIfPresent(section, rendered, "columnWidths");
        copyIfPresent(section, rendered, "borderColor");
        copyIfPresent(section, rendered, "headerFill");
        copyIfPresent(section, rendered, "titleFill");
        copyIfPresent(section, rendered, "photoColumnWidth");
        copyIfPresent(section, rendered, "descriptionColumnWidth");
    }

    private void copyIfPresent(Map<?, ?> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private Map<String, Object> renderTextSection(String title, String text) {
        var lines = new ArrayList<String>();
        if (title != null) {
            lines.add(title);
        }
        if (text != null) {
            lines.add(text);
        }
        return Map.of(
                "type", "TEXT",
                "title", title == null ? "" : title,
                "text", String.join("\n", lines),
                "itemCount", 0,
                "items", List.of());
    }

    private Map<String, Object> renderValueSection(Map<?, ?> section, String title, Map<String, Object> snapshot) {
        var source = firstString(section, "source", "path", "binding");
        var label = firstString(section, "valueLabel", "fieldLabel", "label");
        var value = readPath(snapshot, source).orElse("");
        var valueText = value == null ? "" : String.valueOf(value);
        var lines = new ArrayList<String>();
        if (title != null) {
            lines.add(title);
        }
        lines.add(label == null ? valueText : label + ": " + valueText);
        return Map.of(
                "type", "VALUE",
                "title", title == null ? "" : title,
                "text", String.join("\n", lines),
                "itemCount", valueText.isBlank() ? 0 : 1,
                "items", valueText.isBlank() ? List.of() : List.of(valueText));
    }

    private String renderLayoutItem(Object item, List<Map<String, String>> fields) {
        return fields.stream()
                .map(field -> {
                    var source = field.get("source");
                    var label = field.get("label");
                    var value = readPath(item, source).orElse("");
                    var text = value == null ? "" : String.valueOf(value);
                    return label == null || label.isBlank() ? text : label + ": " + text;
                })
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private List<Map<String, String>> layoutFields(Object value, List<Map<String, String>> defaultFields) {
        if (!(value instanceof List<?> fields) || fields.isEmpty()) {
            return defaultFields;
        }
        var parsed = new ArrayList<Map<String, String>>();
        for (Object field : fields) {
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

    private List<Map<String, String>> defaultPhotoFields() {
        return List.of(
                Map.of("label", "Photo", "source", "photoId"),
                Map.of("label", "Step", "source", "stepCode"),
                Map.of("label", "Working", "source", "workingStorageRef"));
    }

    private List<Map<String, String>> defaultChecklistFields() {
        return List.of(
                Map.of("label", "Item", "source", "itemCode"),
                Map.of("label", "Label", "source", "label"),
                Map.of("label", "Answer", "source", "answer.value"),
                Map.of("label", "Note", "source", "note"));
    }

    public record OutputLayoutBinding(
            Map<String, Object> sections,
            Map<String, Object> templateFields
    ) {
    }
}

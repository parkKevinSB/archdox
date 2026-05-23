package com.archdox.cloud.document.application;

import static com.archdox.cloud.document.application.DocumentSnapshotPath.firstString;
import static com.archdox.cloud.document.application.DocumentSnapshotPath.readPath;
import static com.archdox.cloud.document.application.DocumentSnapshotPath.stringValue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TemplateBindingResolver {
    public Map<String, Object> resolve(Map<String, Object> schema, Map<String, Object> snapshot) {
        var fieldSources = templateFieldSources(schema);
        if (fieldSources.isEmpty()) {
            return Map.of();
        }
        var fields = new LinkedHashMap<String, Object>();
        fieldSources.forEach((fieldName, sourcePath) -> {
            var value = readPath(snapshot, sourcePath).orElse("");
            fields.put(fieldName, value == null ? "" : value);
        });
        return fields;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> templateFieldSources(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return Map.of();
        }
        var sources = new LinkedHashMap<String, String>();
        readFieldSourceMap(schema.get("bindings"), sources);
        readFieldSourceMap(schema.get("fieldMappings"), sources);
        readFieldSourceMap(schema.get("fields"), sources);
        if (schema.get("fields") instanceof java.util.List<?> fields) {
            for (Object field : fields) {
                if (field instanceof Map<?, ?> rawField) {
                    var name = firstString(rawField, "key", "name", "placeholder");
                    var source = firstString(rawField, "source", "path", "binding");
                    if (name != null && source != null) {
                        sources.put(name, source);
                    }
                }
            }
        }
        return sources;
    }

    private void readFieldSourceMap(Object value, Map<String, String> sources) {
        if (!(value instanceof Map<?, ?> mappings)) {
            return;
        }
        mappings.forEach((rawName, rawSource) -> {
            var name = stringValue(rawName);
            var source = fieldSource(rawSource);
            if (name != null && source != null) {
                sources.put(name, source);
            }
        });
    }

    private String fieldSource(Object value) {
        if (value instanceof Map<?, ?> map) {
            return firstString(map, "source", "path", "binding");
        }
        return stringValue(value);
    }
}

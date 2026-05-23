package com.archdox.cloud.document.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class DocumentSnapshotPath {
    private DocumentSnapshotPath() {
    }

    static Optional<Object> readPath(Object root, String path) {
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

    static String firstString(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            var value = map.get(key);
            var text = stringValue(value);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return fallback;
    }

    static List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static Object readSegment(Object current, String segment) {
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
}

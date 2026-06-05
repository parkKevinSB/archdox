package com.archdox.cloud.engine.dto;

import java.util.Map;

public record EngineLegalReferenceResponse(
        String referenceId,
        Long actId,
        String actCode,
        String actName,
        String actType,
        Long articleId,
        String articleKey,
        String articleNo,
        String articleTitle,
        Long legalVersionId,
        String sourceVersionKey,
        String effectiveDate,
        String bindingScope,
        String bindingKey,
        String relevance,
        String catalogCode,
        Integer catalogVersion,
        String checklistItemCode,
        String notes,
        Map<String, Object> metadata
) {
    public EngineLegalReferenceResponse {
        referenceId = text(referenceId);
        actCode = text(actCode);
        actName = text(actName);
        actType = text(actType);
        articleKey = text(articleKey);
        articleNo = text(articleNo);
        articleTitle = text(articleTitle);
        sourceVersionKey = text(sourceVersionKey);
        effectiveDate = text(effectiveDate);
        bindingScope = text(bindingScope);
        bindingKey = text(bindingKey);
        relevance = text(relevance);
        catalogCode = text(catalogCode);
        checklistItemCode = text(checklistItemCode);
        notes = text(notes);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static EngineLegalReferenceResponse from(Map<String, Object> value) {
        var map = value == null ? Map.<String, Object>of() : value;
        return new EngineLegalReferenceResponse(
                text(map.get("referenceId")),
                longValue(map.get("actId")),
                text(map.get("actCode")),
                text(map.get("actName")),
                text(map.get("actType")),
                longValue(map.get("articleId")),
                text(map.get("articleKey")),
                text(map.get("articleNo")),
                text(map.get("articleTitle")),
                longValue(map.get("legalVersionId")),
                text(map.get("sourceVersionKey")),
                text(map.get("effectiveDate")),
                text(map.get("bindingScope")),
                text(map.get("bindingKey")),
                text(map.get("relevance")),
                text(map.get("catalogCode")),
                integerValue(map.get("catalogVersion")),
                text(map.get("checklistItemCode")),
                text(map.get("notes")),
                metadata(map));
    }

    private static Map<String, Object> metadata(Map<String, Object> map) {
        var rawMetadata = map.get("metadata");
        if (rawMetadata instanceof Map<?, ?> metadata) {
            var result = new java.util.LinkedHashMap<String, Object>();
            metadata.forEach((key, value) -> {
                if (key != null && value != null) {
                    result.put(String.valueOf(key), value);
                }
            });
            return Map.copyOf(result);
        }
        return Map.of();
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        var text = text(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        var text = text(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

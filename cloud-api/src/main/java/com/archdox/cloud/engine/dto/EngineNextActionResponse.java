package com.archdox.cloud.engine.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record EngineNextActionResponse(
        String code,
        String label,
        String actionType,
        boolean blocking,
        String targetTool,
        Map<String, Object> metadata
) {
    public EngineNextActionResponse {
        code = text(code);
        label = text(label);
        actionType = text(actionType);
        targetTool = text(targetTool);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static EngineNextActionResponse fromCode(String code) {
        var normalized = text(code).toUpperCase();
        return switch (normalized) {
            case "RESULT_READY" -> response(
                    normalized,
                    "Result ready",
                    "STATE",
                    false,
                    "",
                    Map.of("generationCandidate", true));
            case "FIX_CATALOG_SELECTION" -> response(
                    normalized,
                    "Fix catalog selection",
                    "DATA_FIX",
                    true,
                    "",
                    Map.of("requiredFields", java.util.List.of("tradeCode", "processCode", "inspectionItemCode")));
            case "ANSWER_MISSING_CONTEXT" -> response(
                    normalized,
                    "Answer missing context",
                    "USER_INPUT",
                    true,
                    "",
                    Map.of("source", "normalizedContext.missingQuestions"));
            case "RESOLVE_AMBIGUITY" -> response(
                    normalized,
                    "Resolve ambiguous context",
                    "USER_INPUT",
                    true,
                    "",
                    Map.of("source", "normalizedContext.ambiguities"));
            case "ADD_SUPERVISION_EVIDENCE_CONTEXT" -> response(
                    normalized,
                    "Add supervision evidence context",
                    "USER_INPUT",
                    true,
                    "",
                    Map.of("recommendedFields", java.util.List.of(
                            "supervisionContent",
                            "evidenceText",
                            "photoEvidence",
                            "photoIds",
                            "workArea",
                            "floor")));
            case "RUN_VALIDATION_AGAIN" -> response(
                    normalized,
                    "Run validation again",
                    "ENGINE_TOOL",
                    false,
                    "run_validation",
                    Map.of());
            case "SUBMIT_CONTEXT" -> response(
                    normalized,
                    "Submit context",
                    "ENGINE_TOOL",
                    true,
                    "submit_context_facts",
                    Map.of());
            case "NORMALIZE_CONTEXT" -> response(
                    normalized,
                    "Normalize context",
                    "ENGINE_TOOL",
                    true,
                    "normalize_context",
                    Map.of());
            case "RUN_ENGINE_RECIPE_VALIDATION" -> response(
                    normalized,
                    "Run engine recipe validation",
                    "ENGINE_TOOL",
                    true,
                    "run_validation",
                    Map.of());
            default -> response(
                    normalized,
                    label(normalized),
                    "UNKNOWN",
                    false,
                    "",
                    Map.of());
        };
    }

    public static EngineNextActionResponse fromMap(Map<String, Object> value) {
        var map = value == null ? Map.<String, Object>of() : value;
        var code = text(map.get("code"));
        var fallback = fromCode(code);
        return new EngineNextActionResponse(
                code.isBlank() ? fallback.code() : code,
                firstNonBlank(text(map.get("label")), fallback.label()),
                firstNonBlank(text(map.get("actionType")), fallback.actionType()),
                booleanValue(map.get("blocking"), fallback.blocking()),
                firstNonBlank(text(map.get("targetTool")), fallback.targetTool()),
                objectMap(map.get("metadata")));
    }

    private static EngineNextActionResponse response(
            String code,
            String label,
            String actionType,
            boolean blocking,
            String targetTool,
            Map<String, Object> metadata
    ) {
        return new EngineNextActionResponse(code, label, actionType, blocking, targetTool, metadata);
    }

    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, mapValue) -> {
                if (key != null && mapValue != null) {
                    result.put(String.valueOf(key), mapValue);
                }
            });
            return Map.copyOf(result);
        }
        return Map.of();
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        var text = text(value);
        return text.isBlank() ? fallback : Boolean.parseBoolean(text);
    }

    private static String label(String code) {
        if (code.isBlank()) {
            return "";
        }
        var words = code.toLowerCase().split("_");
        var result = new StringBuilder();
        for (var word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? text(second) : first.trim();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

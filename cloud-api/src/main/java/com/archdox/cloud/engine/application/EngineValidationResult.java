package com.archdox.cloud.engine.application;

import com.archdox.cloud.engine.dto.EngineValidationResultResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record EngineValidationResult(
        String engineRunId,
        ArchDoxEngineResultStatus status,
        boolean generationAllowed,
        String summary,
        List<ArchDoxEngineFinding> findings,
        List<String> nextActions,
        String policyDecision,
        List<String> executedActions,
        String enginePhase,
        Map<String, Object> metadata
) {
    public EngineValidationResult {
        engineRunId = engineRunId == null ? "" : engineRunId.trim();
        status = status == null ? ArchDoxEngineResultStatus.PENDING : status;
        summary = summary == null ? "" : summary.trim();
        findings = findings == null ? List.of() : List.copyOf(findings);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
        policyDecision = policyDecision == null ? "" : policyDecision.trim();
        executedActions = executedActions == null ? List.of() : List.copyOf(executedActions);
        enginePhase = enginePhase == null ? "" : enginePhase.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public EngineValidationResultResponse toResponse() {
        return new EngineValidationResultResponse(
                engineRunId,
                status,
                generationAllowed,
                summary,
                findings,
                nextActions,
                policyDecision,
                executedActions,
                enginePhase,
                metadata);
    }

    public Map<String, Object> toJson() {
        var json = new LinkedHashMap<String, Object>();
        json.put("engineRunId", engineRunId);
        json.put("status", status.name());
        json.put("generationAllowed", generationAllowed);
        json.put("summary", summary);
        json.put("findings", findings.stream().map(EngineValidationResult::findingToJson).toList());
        json.put("nextActions", nextActions);
        json.put("policyDecision", policyDecision);
        json.put("executedActions", executedActions);
        json.put("enginePhase", enginePhase);
        json.put("metadata", metadata);
        return Map.copyOf(json);
    }

    public static EngineValidationResultResponse responseFromJson(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return EngineValidationResultResponse.empty();
        }
        return new EngineValidationResultResponse(
                text(json.get("engineRunId")),
                status(json.get("status")),
                bool(json.get("generationAllowed")),
                text(json.get("summary")),
                findings(json.get("findings")),
                stringList(json.get("nextActions")),
                text(json.get("policyDecision")),
                stringList(json.get("executedActions")),
                text(json.get("enginePhase")),
                objectMap(json.get("metadata")));
    }

    private static Map<String, Object> findingToJson(ArchDoxEngineFinding finding) {
        var json = new LinkedHashMap<String, Object>();
        json.put("code", text(finding.code()));
        json.put("category", text(finding.category()));
        json.put("severity", text(finding.severity()));
        json.put("source", finding.source() == null ? ArchDoxEngineFindingSource.SYSTEM.name() : finding.source().name());
        json.put("location", text(finding.location()));
        json.put("message", text(finding.message()));
        json.put("legalReferences", finding.legalReferences());
        json.put("metadata", finding.metadata());
        return Map.copyOf(json);
    }

    @SuppressWarnings("unchecked")
    private static List<ArchDoxEngineFinding> findings(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        var findings = new ArrayList<ArchDoxEngineFinding>();
        for (var item : items) {
            if (item instanceof Map<?, ?> raw) {
                var map = (Map<String, Object>) raw;
                findings.add(new ArchDoxEngineFinding(
                        text(map.get("code")),
                        text(map.get("category")),
                        text(map.get("severity")),
                        source(map.get("source")),
                        text(map.get("location")),
                        text(map.get("message")),
                        stringList(map.get("legalReferences")),
                        objectMap(map.get("metadata"))));
            }
        }
        return List.copyOf(findings);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return Map.copyOf((Map<String, Object>) map);
        }
        return Map.of();
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(EngineValidationResult::text)
                    .filter(text -> !text.isBlank())
                    .toList();
        }
        return List.of();
    }

    private static ArchDoxEngineResultStatus status(Object value) {
        try {
            return ArchDoxEngineResultStatus.valueOf(text(value).toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ArchDoxEngineResultStatus.PENDING;
        }
    }

    private static ArchDoxEngineFindingSource source(Object value) {
        try {
            return ArchDoxEngineFindingSource.valueOf(text(value).toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ArchDoxEngineFindingSource.SYSTEM;
        }
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(text(value));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

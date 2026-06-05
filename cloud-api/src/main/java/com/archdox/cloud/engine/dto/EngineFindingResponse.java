package com.archdox.cloud.engine.dto;

import com.archdox.cloud.engine.application.ArchDoxEngineFinding;
import java.util.List;
import java.util.Map;

public record EngineFindingResponse(
        String code,
        String category,
        String severity,
        String source,
        String location,
        String message,
        List<String> legalReferenceIds,
        Map<String, Object> metadata
) {
    public EngineFindingResponse {
        code = text(code);
        category = text(category);
        severity = text(severity);
        source = text(source);
        location = text(location);
        message = text(message);
        legalReferenceIds = legalReferenceIds == null ? List.of() : List.copyOf(legalReferenceIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static EngineFindingResponse from(ArchDoxEngineFinding finding) {
        if (finding == null) {
            return new EngineFindingResponse("", "", "", "", "", "", List.of(), Map.of());
        }
        return new EngineFindingResponse(
                finding.code(),
                finding.category(),
                finding.severity(),
                finding.source() == null ? "" : finding.source().name(),
                finding.location(),
                finding.message(),
                finding.legalReferences(),
                finding.metadata());
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}

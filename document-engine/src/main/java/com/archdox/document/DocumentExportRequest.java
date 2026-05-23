package com.archdox.document;

import java.util.Map;

public record DocumentExportRequest(
        String jobId,
        String reportId,
        TemplateSpec template,
        GeneratedArtifact sourceArtifact,
        ArtifactType targetType,
        Map<String, Object> options
) {
}

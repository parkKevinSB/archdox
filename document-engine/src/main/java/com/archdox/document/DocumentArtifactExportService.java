package com.archdox.document;

import java.util.List;

public class DocumentArtifactExportService {
    private final List<DocumentArtifactExporter> exporters;

    public DocumentArtifactExportService(List<DocumentArtifactExporter> exporters) {
        this.exporters = exporters == null ? List.of() : List.copyOf(exporters);
    }

    public static DocumentArtifactExportService disabled() {
        return new DocumentArtifactExportService(List.of());
    }

    public DocumentExportResult export(DocumentExportRequest request) {
        var sourceType = request.sourceArtifact().type();
        var targetType = request.targetType();
        return exporters.stream()
                .filter(exporter -> exporter.supports(sourceType, targetType))
                .findFirst()
                .map(exporter -> exporter.export(request))
                .orElseGet(() -> DocumentExportResult.failed(
                        "DOCUMENT_EXPORTER_NOT_CONFIGURED",
                        "No document artifact exporter configured for %s -> %s".formatted(sourceType, targetType)));
    }
}

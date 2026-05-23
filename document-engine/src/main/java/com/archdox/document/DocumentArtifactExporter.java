package com.archdox.document;

public interface DocumentArtifactExporter {
    boolean supports(ArtifactType sourceType, ArtifactType targetType);

    DocumentExportResult export(DocumentExportRequest request);
}

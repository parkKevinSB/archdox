package com.archdox.document;

public record DocumentExportResult(
        GeneratedArtifact artifact,
        String errorCode,
        String errorMessage
) {
    public static DocumentExportResult completed(GeneratedArtifact artifact) {
        return new DocumentExportResult(artifact, null, null);
    }

    public static DocumentExportResult failed(String errorCode, String errorMessage) {
        return new DocumentExportResult(null, errorCode, errorMessage);
    }

    public boolean isCompleted() {
        return artifact != null;
    }
}

package com.archdox.document;

import java.util.List;

public record DocumentGenerationResult(
        String jobId,
        GenerationStatus status,
        List<GeneratedArtifact> artifacts,
        String errorCode,
        String errorMessage
) {
    public static DocumentGenerationResult completed(String jobId, List<GeneratedArtifact> artifacts) {
        return new DocumentGenerationResult(jobId, GenerationStatus.COMPLETED, artifacts, null, null);
    }

    public static DocumentGenerationResult failed(String jobId, String errorCode, String errorMessage) {
        return new DocumentGenerationResult(jobId, GenerationStatus.FAILED, List.of(), errorCode, errorMessage);
    }
}

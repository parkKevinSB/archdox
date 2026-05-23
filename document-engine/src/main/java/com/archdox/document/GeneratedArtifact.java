package com.archdox.document;

public record GeneratedArtifact(
        ArtifactType type,
        String fileName,
        String storageRef,
        long bytes,
        String sha256,
        byte[] content
) {
    public GeneratedArtifact(
            ArtifactType type,
            String fileName,
            String storageRef,
            long bytes,
            String sha256
    ) {
        this(type, fileName, storageRef, bytes, sha256, null);
    }
}

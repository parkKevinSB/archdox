package com.archdox.cloud.document.dto;

import com.archdox.cloud.document.domain.DocumentArtifactStorageKind;
import com.archdox.cloud.document.domain.DocumentArtifactType;
import java.time.OffsetDateTime;

public record DocumentArtifactResponse(
        Long id,
        DocumentArtifactType artifactType,
        DocumentArtifactStorageKind storageKind,
        String storageRef,
        String fileName,
        String mimeType,
        long bytes,
        String hashSha256,
        OffsetDateTime createdAt
) {
}

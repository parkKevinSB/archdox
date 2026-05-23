package com.archdox.cloud.officeops.dto;

import com.archdox.cloud.document.domain.DocumentArtifactStorageKind;
import com.archdox.cloud.document.domain.DocumentArtifactType;
import java.time.OffsetDateTime;

public record DocumentArtifactOpsResponse(
        Long id,
        Long documentJobId,
        Long reportId,
        DocumentArtifactType artifactType,
        DocumentArtifactStorageKind storageKind,
        String fileName,
        String mimeType,
        long bytes,
        String hashSha256,
        OffsetDateTime createdAt
) {
}

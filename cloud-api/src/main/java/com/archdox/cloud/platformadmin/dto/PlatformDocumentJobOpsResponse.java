package com.archdox.cloud.platformadmin.dto;

import com.archdox.cloud.document.domain.DocumentJobProgressStep;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.document.OutputFormat;
import java.time.OffsetDateTime;

public record PlatformDocumentJobOpsResponse(
        Long id,
        Long officeId,
        Long reportId,
        Long projectId,
        int reportRevision,
        DocumentJobStatus status,
        DocumentJobProgressStep progressStep,
        int progressPercent,
        DocumentWorkerType workerType,
        OutputFormat outputFormat,
        String errorCode,
        String errorMessage,
        OffsetDateTime requestedAt,
        OffsetDateTime updatedAt
) {
}

package com.archdox.cloud.officeops.dto;

import com.archdox.cloud.document.domain.DocumentJobProgressStep;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.document.OutputFormat;
import java.time.OffsetDateTime;
import java.util.List;

public record DocumentJobOpsResponse(
        Long id,
        Long officeId,
        Long reportId,
        Long projectId,
        int reportRevision,
        Long templateId,
        DocumentJobStatus status,
        DocumentJobProgressStep progressStep,
        int progressPercent,
        String progressMessage,
        Long requestedBy,
        DocumentWorkerType workerType,
        OutputFormat outputFormat,
        String errorCode,
        String errorMessage,
        OffsetDateTime requestedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt,
        List<DocumentArtifactOpsResponse> artifacts
) {
}

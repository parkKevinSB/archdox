package com.archdox.cloud.document.dto;

import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.domain.DocumentJobProgressStep;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.document.OutputFormat;
import java.time.OffsetDateTime;
import java.util.List;

public record DocumentJobResponse(
        Long id,
        Long officeId,
        Long reportId,
        Long projectId,
        int reportRevision,
        DocumentJobStatus status,
        DocumentJobProgressStep progressStep,
        int progressPercent,
        String progressMessage,
        DocumentWorkerType workerType,
        OutputFormat outputFormat,
        String errorCode,
        String errorMessage,
        OffsetDateTime requestedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        List<DocumentArtifactResponse> artifacts
) {
}

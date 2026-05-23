package com.archdox.cloud.document.dto;

import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.document.OutputFormat;

public record CreateDocumentJobRequest(
        OutputFormat outputFormat,
        DocumentWorkerType workerType
) {
    public OutputFormat normalizedOutputFormat() {
        return outputFormat == null ? OutputFormat.DOCX : outputFormat;
    }

    public DocumentWorkerType normalizedWorkerType() {
        return workerType == null ? DocumentWorkerType.CLOUD : workerType;
    }
}

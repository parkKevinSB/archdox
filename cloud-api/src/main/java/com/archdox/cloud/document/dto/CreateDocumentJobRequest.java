package com.archdox.cloud.document.dto;

import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.document.OutputFormat;

public record CreateDocumentJobRequest(
        OutputFormat outputFormat,
        DocumentWorkerType workerType,
        DocumentSignatureRequest signature
) {
    public OutputFormat normalizedOutputFormat() {
        return outputFormat == null ? OutputFormat.DOCX : outputFormat;
    }

    public record DocumentSignatureRequest(
            String signedByName,
            String signedByRole,
            String signatureImageDataUrl,
            String signatureImageMimeType
    ) {
    }
}

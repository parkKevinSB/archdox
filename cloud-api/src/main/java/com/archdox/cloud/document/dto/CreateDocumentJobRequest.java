package com.archdox.cloud.document.dto;

import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.document.OutputFormat;
import java.util.List;

public record CreateDocumentJobRequest(
        OutputFormat outputFormat,
        DocumentWorkerType workerType,
        DocumentSignatureRequest signature,
        List<DocumentRenderOverrideRequest> renderOverrides
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

    public record DocumentRenderOverrideRequest(
            String path,
            String value,
            String label,
            String source
    ) {
    }
}

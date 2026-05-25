package com.archdox.cloud.document.application;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.document.OutputFormat;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DocumentGenerationRoutingService {
    private final ArchDoxAgentCommandService agentCommandService;

    public DocumentGenerationRoutingService(ArchDoxAgentCommandService agentCommandService) {
        this.agentCommandService = agentCommandService;
    }

    public DocumentWorkerType route(
            Long officeId,
            OutputFormat outputFormat,
            DocumentWorkerType requestedWorkerType
    ) {
        var normalizedOutputFormat = outputFormat == null ? OutputFormat.DOCX : outputFormat;
        if (requestedWorkerType != null) {
            validateExplicitRoute(officeId, normalizedOutputFormat, requestedWorkerType);
            return requestedWorkerType;
        }
        if (agentCommandService.hasDocumentRenderTarget(officeId, normalizedOutputFormat)) {
            return DocumentWorkerType.ARCHDOX_AGENT;
        }
        throw unavailable(normalizedOutputFormat);
    }

    private void validateExplicitRoute(
            Long officeId,
            OutputFormat outputFormat,
            DocumentWorkerType workerType
    ) {
        if (workerType == DocumentWorkerType.ARCHDOX_AGENT
                && agentCommandService.hasDocumentRenderTarget(officeId, outputFormat)) {
            return;
        }
        throw unsupported(workerType, outputFormat);
    }

    private BadRequestException unavailable(OutputFormat outputFormat) {
        return new BadRequestException(
                "DOCUMENT_WORKER_UNAVAILABLE",
                "errors.document.workerUnavailable",
                "No ArchDox Agent is available for output format " + outputFormat,
                Map.of("outputFormat", outputFormat.name()));
    }

    private BadRequestException unsupported(DocumentWorkerType workerType, OutputFormat outputFormat) {
        return new BadRequestException(
                "DOCUMENT_WORKER_UNSUPPORTED",
                "errors.document.workerUnsupported",
                "Document worker " + workerType + " does not support output format " + outputFormat,
                Map.of(
                        "workerType", workerType.name(),
                        "outputFormat", outputFormat.name()));
    }
}

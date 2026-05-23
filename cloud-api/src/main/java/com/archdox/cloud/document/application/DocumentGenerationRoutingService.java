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
    private final DocumentExportProperties exportProperties;

    public DocumentGenerationRoutingService(
            ArchDoxAgentCommandService agentCommandService,
            DocumentExportProperties exportProperties
    ) {
        this.agentCommandService = agentCommandService;
        this.exportProperties = exportProperties;
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
        if (cloudSupports(normalizedOutputFormat)) {
            return DocumentWorkerType.CLOUD;
        }
        throw unavailable(normalizedOutputFormat);
    }

    private void validateExplicitRoute(
            Long officeId,
            OutputFormat outputFormat,
            DocumentWorkerType workerType
    ) {
        if (workerType == DocumentWorkerType.CLOUD && cloudSupports(outputFormat)) {
            return;
        }
        if (workerType == DocumentWorkerType.ARCHDOX_AGENT
                && agentCommandService.hasDocumentRenderTarget(officeId, outputFormat)) {
            return;
        }
        throw unsupported(workerType, outputFormat);
    }

    private boolean cloudSupports(OutputFormat outputFormat) {
        return switch (outputFormat) {
            case DOCX, HTML -> true;
            case PDF, DOCX_AND_PDF, HTML_AND_PDF -> exportProperties.getLibreOffice().isEnabled();
            case HWP, HWPX -> false;
        };
    }

    private BadRequestException unavailable(OutputFormat outputFormat) {
        return new BadRequestException(
                "DOCUMENT_WORKER_UNAVAILABLE",
                "errors.document.workerUnavailable",
                "No document worker is available for output format " + outputFormat,
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

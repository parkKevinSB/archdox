package com.archdox.cloud.document.application;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.document.OutputFormat;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DocumentGenerationRoutingService {
    private static final String REPORT_TYPE_CHECKLIST = "CONSTRUCTION_SUPERVISION_CHECKLIST";

    private final ArchDoxAgentCommandService agentCommandService;

    public DocumentGenerationRoutingService(ArchDoxAgentCommandService agentCommandService) {
        this.agentCommandService = agentCommandService;
    }

    public DocumentWorkerType route(
            Long officeId,
            String reportType,
            OutputFormat outputFormat,
            DocumentWorkerType requestedWorkerType
    ) {
        var normalizedOutputFormat = outputFormat == null ? OutputFormat.DOCX : outputFormat;
        if (isChecklistReport(reportType)) {
            if (requestedWorkerType != null) {
                validateExplicitRoute(officeId, normalizedOutputFormat, requestedWorkerType, true);
                return requestedWorkerType;
            }
            if (isCloudApiChecklistFormat(normalizedOutputFormat)) {
                return DocumentWorkerType.CLOUD_API;
            }
            throw unsupported(DocumentWorkerType.CLOUD_API, normalizedOutputFormat);
        }
        if (requestedWorkerType != null) {
            validateExplicitRoute(officeId, normalizedOutputFormat, requestedWorkerType, false);
            return requestedWorkerType;
        }
        if (agentCommandService.hasDocumentRenderTarget(officeId, normalizedOutputFormat)) {
            return DocumentWorkerType.ARCHDOX_AGENT;
        }
        throw unavailable(normalizedOutputFormat);
    }

    public DocumentWorkerType route(
            Long officeId,
            OutputFormat outputFormat,
            DocumentWorkerType requestedWorkerType
    ) {
        return route(officeId, null, outputFormat, requestedWorkerType);
    }

    private void validateExplicitRoute(
            Long officeId,
            OutputFormat outputFormat,
            DocumentWorkerType workerType,
            boolean allowCloudApi
    ) {
        if (workerType == DocumentWorkerType.ARCHDOX_AGENT
                && agentCommandService.hasDocumentRenderTarget(officeId, outputFormat)) {
            return;
        }
        if (allowCloudApi && workerType == DocumentWorkerType.CLOUD_API && isCloudApiChecklistFormat(outputFormat)) {
            return;
        }
        throw unsupported(workerType, outputFormat);
    }

    private boolean isChecklistReport(String reportType) {
        return REPORT_TYPE_CHECKLIST.equals(reportType);
    }

    private boolean isCloudApiChecklistFormat(OutputFormat outputFormat) {
        return outputFormat == OutputFormat.DOCX || outputFormat == OutputFormat.PDF;
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

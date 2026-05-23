package com.archdox.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DocumentGenerationArtifacts {
    private DocumentGenerationArtifacts() {
    }

    static DocumentGenerationResult completeFromDocx(
            DocumentGenerationRequest request,
            GeneratedArtifact docxArtifact,
            DocumentArtifactExportService exportService
    ) {
        var format = request.outputFormat() == null ? OutputFormat.DOCX : request.outputFormat();
        var artifacts = new ArrayList<GeneratedArtifact>();
        if (includesDocx(format)) {
            artifacts.add(docxArtifact);
        }
        for (var targetType : exportTargets(format)) {
            var export = exportService.export(new DocumentExportRequest(
                    request.jobId(),
                    request.reportId(),
                    request.template(),
                    docxArtifact,
                    targetType,
                    Map.of("outputFormat", format.name())));
            if (!export.isCompleted()) {
                return DocumentGenerationResult.failed(request.jobId(), export.errorCode(), export.errorMessage());
            }
            artifacts.add(export.artifact());
        }
        return DocumentGenerationResult.completed(request.jobId(), artifacts);
    }

    private static boolean includesDocx(OutputFormat format) {
        return switch (format) {
            case DOCX, DOCX_AND_PDF -> true;
            case HTML, HTML_AND_PDF, PDF, HWP, HWPX -> false;
        };
    }

    private static List<ArtifactType> exportTargets(OutputFormat format) {
        return switch (format) {
            case DOCX -> List.of();
            case HTML -> List.of(ArtifactType.HTML);
            case HTML_AND_PDF -> List.of(ArtifactType.HTML, ArtifactType.PDF);
            case PDF, DOCX_AND_PDF -> List.of(ArtifactType.PDF);
            case HWP -> List.of(ArtifactType.HWP);
            case HWPX -> List.of(ArtifactType.HWPX);
        };
    }
}

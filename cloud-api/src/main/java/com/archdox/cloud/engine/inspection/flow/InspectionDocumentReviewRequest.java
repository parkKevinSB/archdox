package com.archdox.cloud.engine.inspection.flow;

import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.engine.dto.EngineContextFactRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InspectionDocumentReviewRequest(
        String requestId,
        EngineApiPrincipal principal,
        String customerProjectRef,
        String reviewPurpose,
        String documentTypeHint,
        String fileName,
        String contentText,
        String targetDate,
        Map<String, Object> inputMetadata,
        List<EngineContextFactRequest> suppliedFacts,
        InspectionDocumentReviewState state
) {
    public InspectionDocumentReviewRequest {
        requestId = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();
        reviewPurpose = reviewPurpose == null || reviewPurpose.isBlank() ? "preflight" : reviewPurpose.trim();
        documentTypeHint = documentTypeHint == null || documentTypeHint.isBlank()
                ? "CONSTRUCTION_DAILY_SUPERVISION_LOG"
                : documentTypeHint.trim();
        fileName = fileName == null || fileName.isBlank() ? "inspection-document.txt" : fileName.trim();
        contentText = contentText == null ? "" : contentText.trim();
        targetDate = targetDate == null ? "" : targetDate.trim();
        inputMetadata = inputMetadata == null ? Map.of() : Map.copyOf(inputMetadata);
        suppliedFacts = suppliedFacts == null ? List.of() : List.copyOf(suppliedFacts);
        state = state == null ? new InspectionDocumentReviewState() : state;
    }
}

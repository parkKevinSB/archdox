package com.archdox.cloud.engine.inspection.flow;

import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.engine.dto.EngineContextFactRequest;
import java.util.List;
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
        suppliedFacts = suppliedFacts == null ? List.of() : List.copyOf(suppliedFacts);
        state = state == null ? new InspectionDocumentReviewState() : state;
    }
}

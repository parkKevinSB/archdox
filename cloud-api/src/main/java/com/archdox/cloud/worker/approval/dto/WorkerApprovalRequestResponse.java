package com.archdox.cloud.worker.approval.dto;

import com.archdox.cloud.worker.approval.domain.WorkerApprovalRequestStatus;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record WorkerApprovalRequestResponse(
        Long id,
        Long officeId,
        WorkerApprovalRequestStatus status,
        UUID workerRequestId,
        UUID executionRequestId,
        ArchDoxWorkerRequestSource requestSource,
        String command,
        Long userId,
        Long projectId,
        Long siteId,
        Long reportId,
        Long documentJobId,
        String locale,
        ArchDoxWorkerActionType actionType,
        ArchDoxWorkerActionOrigin actionOrigin,
        String actionReason,
        double confidence,
        Map<String, Object> actionPayload,
        String decisionCode,
        String decisionMessage,
        Long decidedByUserId,
        String decisionReason,
        OffsetDateTime requestedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime decidedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

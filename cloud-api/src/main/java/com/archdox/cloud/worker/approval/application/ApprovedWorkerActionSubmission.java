package com.archdox.cloud.worker.approval.application;

import com.archdox.cloud.worker.approval.dto.WorkerApprovalRequestResponse;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerRequest;

public record ApprovedWorkerActionSubmission(
        WorkerApprovalRequestResponse response,
        ArchDoxWorkerRequest request,
        ArchDoxWorkerAction action
) {
}

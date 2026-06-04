package com.archdox.cloud.worker.approval.application;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.worker.ArchDoxWorkerServiceWorker;
import com.archdox.cloud.worker.approval.dto.WorkerApprovalRequestResponse;
import com.archdox.worker.flow.ArchDoxWorkerExecutionFlowFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class WorkerApprovalExecutionService {
    private final WorkerApprovalRequestService approvalRequestService;
    private final ObjectProvider<ArchDoxWorkerExecutionFlowFactory> flowFactoryProvider;
    private final ObjectProvider<ArchDoxWorkerServiceWorker> workerProvider;

    public WorkerApprovalExecutionService(
            WorkerApprovalRequestService approvalRequestService,
            ObjectProvider<ArchDoxWorkerExecutionFlowFactory> flowFactoryProvider,
            ObjectProvider<ArchDoxWorkerServiceWorker> workerProvider
    ) {
        this.approvalRequestService = approvalRequestService;
        this.flowFactoryProvider = flowFactoryProvider;
        this.workerProvider = workerProvider;
    }

    @Transactional
    public WorkerApprovalRequestResponse approveAndSubmit(
            UserPrincipal principal,
            Long approvalRequestId,
            String reason
    ) {
        var submission = approvalRequestService.approve(principal, approvalRequestId, reason);
        submitAfterCommit(submission);
        return submission.response();
    }

    @Transactional
    public WorkerApprovalRequestResponse reject(
            UserPrincipal principal,
            Long approvalRequestId,
            String reason
    ) {
        return approvalRequestService.reject(principal, approvalRequestId, reason);
    }

    private void submitAfterCommit(ApprovedWorkerActionSubmission submission) {
        var task = (Runnable) () -> workerProvider.getObject()
                .submit(flowFactoryProvider.getObject().create(submission.request(), submission.action()));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}

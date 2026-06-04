package com.archdox.cloud.document.application;

import com.archdox.cloud.document.dto.CreateDocumentJobRequest;
import com.archdox.cloud.document.dto.DocumentJobResponse;
import com.archdox.cloud.document.event.DocumentGenerationRequested;
import com.archdox.cloud.document.flow.DocumentGenerationFlowFactory;
import com.archdox.cloud.document.flow.DocumentGenerationWorker;
import com.archdox.cloud.global.security.UserPrincipal;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DocumentGenerationRequestService {
    private final DocumentJobService documentJobService;
    private final DocumentGenerationFlowFactory flowFactory;
    private final DocumentGenerationWorker worker;

    public DocumentGenerationRequestService(
            DocumentJobService documentJobService,
            DocumentGenerationFlowFactory flowFactory,
            DocumentGenerationWorker worker
    ) {
        this.documentJobService = documentJobService;
        this.flowFactory = flowFactory;
        this.worker = worker;
    }

    @Transactional
    public DocumentJobResponse request(Long reportId, CreateDocumentJobRequest request, UserPrincipal principal) {
        var response = documentJobService.create(reportId, request, principal);
        registerAfterCommit(() -> worker.submit(flowFactory.create(new DocumentGenerationRequested(
                response.officeId(),
                response.reportId(),
                response.id(),
                response.workerType(),
                OffsetDateTime.now()))));
        return response;
    }

    private void registerAfterCommit(Runnable task) {
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

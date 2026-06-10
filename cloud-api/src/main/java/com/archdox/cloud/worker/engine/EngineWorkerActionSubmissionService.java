package com.archdox.cloud.worker.engine;

import com.archdox.cloud.engine.application.EngineValidationResult;
import com.archdox.cloud.worker.ArchDoxWorkerServiceWorker;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.flow.ArchDoxWorkerExecutionFlowFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class EngineWorkerActionSubmissionService {
    private final EngineWorkerActionSuggestionBridge suggestionBridge;
    private final ObjectProvider<ArchDoxWorkerExecutionFlowFactory> flowFactoryProvider;
    private final ObjectProvider<ArchDoxWorkerServiceWorker> workerProvider;

    public EngineWorkerActionSubmissionService(
            EngineWorkerActionSuggestionBridge suggestionBridge,
            ObjectProvider<ArchDoxWorkerExecutionFlowFactory> flowFactoryProvider,
            ObjectProvider<ArchDoxWorkerServiceWorker> workerProvider
    ) {
        this.suggestionBridge = suggestionBridge;
        this.flowFactoryProvider = flowFactoryProvider;
        this.workerProvider = workerProvider;
    }

    public EngineWorkerActionSubmissionResult submitAfterCommit(
            EngineValidationResult engineResult,
            EngineWorkerActionSubmissionRequest request
    ) {
        if (engineResult == null) {
            return EngineWorkerActionSubmissionResult.empty();
        }
        var candidates = suggestionBridge.candidates(engineResult, request.basePayload());
        var candidateMetadata = new ArrayList<Map<String, Object>>();
        var submitted = new ArrayList<Map<String, Object>>();
        var skipped = new ArrayList<Map<String, Object>>();
        for (var candidate : candidates) {
            candidateMetadata.add(candidate.toMetadata());
            var skipReason = skipReason(candidate, request);
            if (!skipReason.isBlank()) {
                skipped.add(skipMetadata(candidate, skipReason));
                continue;
            }
            var workerRequest = new ArchDoxWorkerRequest(
                    request.requestId(),
                    request.source(),
                    request.command(),
                    request.context(),
                    Instant.now());
            submitWorkerFlowAfterCommit(workerRequest, candidate.action());
            submitted.add(submittedMetadata(candidate));
        }
        return new EngineWorkerActionSubmissionResult(candidateMetadata, submitted, skipped);
    }

    private void submitWorkerFlowAfterCommit(ArchDoxWorkerRequest request, ArchDoxWorkerAction action) {
        var task = (Runnable) () -> workerProvider.getObject()
                .submit(flowFactoryProvider.getObject().create(request, action));
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

    private String skipReason(
            EngineWorkerActionCandidate candidate,
            EngineWorkerActionSubmissionRequest request
    ) {
        if (candidate.action() == null) {
            return candidate.reason().isBlank() ? "Engine suggestion cannot be converted to Worker action." : candidate.reason();
        }
        if (!candidate.known()) {
            return "Suggested Worker action is not known.";
        }
        if (!candidate.enabled()) {
            return "Suggested Worker action is not enabled.";
        }
        if (!candidate.executorRegistered()) {
            return "Suggested Worker action has no registered executor.";
        }
        if (request.excludedActionTypes().contains(candidate.action().actionType())) {
            return "Suggested Worker action is excluded for this Engine workflow to prevent recursion or unsafe chaining.";
        }
        return "";
    }

    private Map<String, Object> submittedMetadata(EngineWorkerActionCandidate candidate) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("suggestedAction", candidate.suggestedAction());
        metadata.put("actionType", candidate.action().actionType().name());
        metadata.put("reason", "Submitted to ArchDox Worker Flower execution flow.");
        return Map.copyOf(metadata);
    }

    private Map<String, Object> skipMetadata(EngineWorkerActionCandidate candidate, String reason) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("suggestedAction", candidate.suggestedAction());
        metadata.put("actionType", candidate.action() == null ? "" : candidate.action().actionType().name());
        metadata.put("reason", reason);
        return Map.copyOf(metadata);
    }

}

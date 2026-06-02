package com.archdox.worker.application;

import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerPolicyDecision;
import com.archdox.worker.domain.ArchDoxWorkerRequest;

public interface ArchDoxWorkerPolicyGate {
    ArchDoxWorkerPolicyDecision evaluate(ArchDoxWorkerRequest request, ArchDoxWorkerAction action);

    static ArchDoxWorkerPolicyGate allowAll() {
        return (request, action) -> ArchDoxWorkerPolicyDecision.allow();
    }

    static ArchDoxWorkerPolicyGate denyAll(String reasonCode, String message) {
        return (request, action) -> ArchDoxWorkerPolicyDecision.deny(reasonCode, message);
    }
}

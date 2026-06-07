package com.archdox.worker.application;

import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionDefinition;
import com.archdox.worker.domain.ArchDoxWorkerRequest;

@FunctionalInterface
public interface ArchDoxWorkerRunControl {
    ArchDoxWorkerRunControlDecision check(
            ArchDoxWorkerRequest request,
            ArchDoxWorkerAction action,
            ArchDoxWorkerActionDefinition definition);

    static ArchDoxWorkerRunControl allowAll() {
        return (request, action, definition) -> ArchDoxWorkerRunControlDecision.allow();
    }
}

package com.archdox.worker.application;

import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionDefinition;
import com.archdox.worker.domain.ArchDoxWorkerRequest;

public record ArchDoxWorkerExecutionContext(
        ArchDoxWorkerRequest request,
        ArchDoxWorkerAction action,
        ArchDoxWorkerActionDefinition definition
) {
    public ArchDoxWorkerExecutionContext(ArchDoxWorkerRequest request, ArchDoxWorkerAction action) {
        this(request, action, null);
    }
}

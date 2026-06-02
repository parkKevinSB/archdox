package com.archdox.worker.application;

import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerRequest;

public record ArchDoxWorkerExecutionContext(
        ArchDoxWorkerRequest request,
        ArchDoxWorkerAction action
) {
}

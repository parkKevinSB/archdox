package com.archdox.worker.application;

import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionType;

public interface ArchDoxWorkerActionExecutor {
    ArchDoxWorkerActionType actionType();

    ArchDoxWorkerActionResult execute(ArchDoxWorkerExecutionContext context);
}

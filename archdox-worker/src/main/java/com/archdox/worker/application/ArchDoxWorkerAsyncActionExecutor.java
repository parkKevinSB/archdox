package com.archdox.worker.application;

import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import java.util.concurrent.CompletableFuture;

public interface ArchDoxWorkerAsyncActionExecutor extends ArchDoxWorkerActionExecutor {
    CompletableFuture<ArchDoxWorkerActionResult> executeAsync(ArchDoxWorkerExecutionContext context);

    @Override
    default ArchDoxWorkerActionResult execute(ArchDoxWorkerExecutionContext context) {
        return executeAsync(context).join();
    }
}

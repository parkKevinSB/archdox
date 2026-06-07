package com.archdox.worker.flow;

import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import io.github.parkkevinsb.flower.core.flow.Flow;

public record ArchDoxWorkerExecutionHandle(
        Flow flow,
        ArchDoxWorkerExecutionSession session
) {
    public ArchDoxWorkerActionResult result() {
        return session.result();
    }
}

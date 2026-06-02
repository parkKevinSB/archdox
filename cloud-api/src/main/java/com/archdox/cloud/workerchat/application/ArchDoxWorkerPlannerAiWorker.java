package com.archdox.cloud.workerchat.application;

import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class ArchDoxWorkerPlannerAiWorker {
    private final Engine engine;

    public ArchDoxWorkerPlannerAiWorker(Engine engine) {
        this.engine = engine;
    }

    public boolean submitAndAwait(AiHarnessFlow flow, Duration timeout) {
        engine.worker(ArchDoxRuntimeConfiguration.ARCHDOX_WORKER_PLANNER_AI_WORKER)
                .submit(flow.flow(), DuplicatePolicy.REJECT);
        var deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (flow.flow().state().isTerminal()) {
                return true;
            }
            sleep();
        }
        flow.flow().cancel();
        return false;
    }

    private void sleep() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for conversation planner AI harness", ex);
        }
    }
}

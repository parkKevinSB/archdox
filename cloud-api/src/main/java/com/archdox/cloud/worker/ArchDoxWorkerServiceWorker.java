package com.archdox.cloud.worker;

import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class ArchDoxWorkerServiceWorker {
    private final Engine engine;

    public ArchDoxWorkerServiceWorker(Engine engine) {
        this.engine = engine;
    }

    public void submit(Flow flow) {
        engine.worker(ArchDoxRuntimeConfiguration.ARCHDOX_WORKER_SERVICE_WORKER)
                .submit(flow, DuplicatePolicy.REJECT);
    }

    public boolean submitAndAwait(Flow flow, Duration timeout) {
        submit(flow);
        var deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (flow.state().isTerminal()) {
                return true;
            }
            sleep();
        }
        flow.cancel();
        return false;
    }

    private void sleep() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for ArchDox worker execution", ex);
        }
    }
}

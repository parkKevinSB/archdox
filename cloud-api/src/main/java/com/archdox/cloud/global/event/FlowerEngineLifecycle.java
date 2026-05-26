package com.archdox.cloud.global.event;

import io.github.parkkevinsb.flower.core.engine.Engine;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class FlowerEngineLifecycle implements SmartLifecycle {
    private static final int PHASE = Integer.MAX_VALUE;

    private final Engine engine;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public FlowerEngineLifecycle(Engine engine) {
        this.engine = engine;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        engine.start();
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        engine.stop();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}

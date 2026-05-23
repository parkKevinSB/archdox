package com.archdox.cloud.global.event;

import io.github.parkkevinsb.flower.core.engine.Engine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class FlowerEngineLifecycle {
    private final Engine engine;

    public FlowerEngineLifecycle(Engine engine) {
        this.engine = engine;
    }

    @PostConstruct
    public void start() {
        engine.start();
    }

    @PreDestroy
    public void stop() {
        engine.stop();
    }
}

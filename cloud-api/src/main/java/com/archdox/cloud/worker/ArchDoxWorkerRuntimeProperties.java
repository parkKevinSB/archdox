package com.archdox.cloud.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.worker")
public class ArchDoxWorkerRuntimeProperties {
    private long workerIntervalMs = 250;

    public long getWorkerIntervalMs() {
        return workerIntervalMs;
    }

    public void setWorkerIntervalMs(long workerIntervalMs) {
        this.workerIntervalMs = workerIntervalMs;
    }

    public long safeWorkerIntervalMs() {
        return Math.max(100, workerIntervalMs);
    }
}

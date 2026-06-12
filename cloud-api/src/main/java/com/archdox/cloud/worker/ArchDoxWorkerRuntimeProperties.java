package com.archdox.cloud.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.worker")
public class ArchDoxWorkerRuntimeProperties {
    private long workerIntervalMs = 250;
    private int actionExecutorThreads = 4;
    private int actionExecutorQueueCapacity = 100;

    public long getWorkerIntervalMs() {
        return workerIntervalMs;
    }

    public void setWorkerIntervalMs(long workerIntervalMs) {
        this.workerIntervalMs = workerIntervalMs;
    }

    public int getActionExecutorThreads() {
        return actionExecutorThreads;
    }

    public void setActionExecutorThreads(int actionExecutorThreads) {
        this.actionExecutorThreads = actionExecutorThreads;
    }

    public int getActionExecutorQueueCapacity() {
        return actionExecutorQueueCapacity;
    }

    public void setActionExecutorQueueCapacity(int actionExecutorQueueCapacity) {
        this.actionExecutorQueueCapacity = actionExecutorQueueCapacity;
    }

    public long safeWorkerIntervalMs() {
        return Math.max(100, workerIntervalMs);
    }

    public int safeActionExecutorThreads() {
        return Math.max(1, actionExecutorThreads);
    }

    public int safeActionExecutorQueueCapacity() {
        return Math.max(1, actionExecutorQueueCapacity);
    }
}

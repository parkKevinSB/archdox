package com.archdox.cloud.aipolicy.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.ai.model-gateway.execution")
public class AiModelGatewayExecutionProperties {
    private int threads = 8;
    private int queueCapacity = 200;

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int safeThreads() {
        return Math.max(1, Math.min(threads, 64));
    }

    public int safeQueueCapacity() {
        return Math.max(1, Math.min(queueCapacity, 10_000));
    }
}

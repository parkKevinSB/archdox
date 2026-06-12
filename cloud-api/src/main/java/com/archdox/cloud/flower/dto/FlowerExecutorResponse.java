package com.archdox.cloud.flower.dto;

public record FlowerExecutorResponse(
        String beanName,
        String state,
        String queueType,
        int corePoolSize,
        int maximumPoolSize,
        int poolSize,
        int largestPoolSize,
        int activeCount,
        int queueSize,
        Integer remainingQueueCapacity,
        long taskCount,
        long completedTaskCount,
        boolean shutdown,
        boolean terminating,
        boolean terminated
) {
}

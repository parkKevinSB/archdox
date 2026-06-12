package com.archdox.cloud.aipolicy.application;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiModelGatewayExecutionConfiguration {
    public static final String AI_MODEL_GATEWAY_EXECUTOR = "aiModelGatewayExecutor";

    @Bean(name = AI_MODEL_GATEWAY_EXECUTOR, destroyMethod = "shutdown")
    ExecutorService aiModelGatewayExecutor(AiModelGatewayExecutionProperties properties) {
        var sequence = new AtomicInteger();
        return new ThreadPoolExecutor(
                properties.safeThreads(),
                properties.safeThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.safeQueueCapacity()),
                runnable -> {
                    var thread = new Thread(runnable, "ai-model-gateway-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}

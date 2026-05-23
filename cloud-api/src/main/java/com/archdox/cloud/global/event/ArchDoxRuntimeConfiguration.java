package com.archdox.cloud.global.event;

import com.archdox.cloud.document.application.DocumentGenerationProperties;
import com.archdox.cloud.document.application.DocumentDeliveryProperties;
import com.archdox.cloud.photo.application.PhotoDerivativeProperties;
import com.archdox.cloud.photo.application.PhotoPickupProperties;
import io.github.parkkevinsb.bloom.EventBus;
import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.bloom.BloomEventBus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArchDoxRuntimeConfiguration {
    public static final String PHOTO_DERIVATIVE_WORKER = "photo-derivatives";
    public static final String PHOTO_PICKUP_WORKER = "photo-pickup";
    public static final String DOCUMENT_GENERATION_WORKER = "document-generation";
    public static final String DOCUMENT_DELIVERY_WORKER = "document-delivery";

    @Bean
    EventBus archDoxEventBus() {
        return LocalEventBus.create();
    }

    @Bean
    Engine archDoxFlowerEngine(
            EventBus eventBus,
            PhotoDerivativeProperties photoProperties,
            PhotoPickupProperties photoPickupProperties,
            DocumentGenerationProperties documentProperties,
            DocumentDeliveryProperties documentDeliveryProperties
    ) {
        return Engine.builder()
                .eventBus(BloomEventBus.wrap(eventBus))
                .worker(Worker.builder(PHOTO_DERIVATIVE_WORKER)
                        .intervalMillis(photoProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(PHOTO_PICKUP_WORKER)
                        .intervalMillis(photoPickupProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(DOCUMENT_GENERATION_WORKER)
                        .intervalMillis(documentProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(DOCUMENT_DELIVERY_WORKER)
                        .intervalMillis(documentDeliveryProperties.safeWorkerIntervalMs())
                        .build())
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService photoDerivativeExecutor(PhotoDerivativeProperties properties) {
        var sequence = new AtomicInteger();
        return Executors.newFixedThreadPool(properties.safeExecutorThreads(), runnable -> {
            var thread = new Thread(runnable, "photo-derivative-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService documentGenerationExecutor(DocumentGenerationProperties properties) {
        var sequence = new AtomicInteger();
        return Executors.newFixedThreadPool(properties.safeExecutorThreads(), runnable -> {
            var thread = new Thread(runnable, "document-generation-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}

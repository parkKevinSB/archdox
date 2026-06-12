package com.archdox.cloud.global.event;

import com.archdox.cloud.agent.application.ArchDoxAgentConnectionHealthProperties;
import com.archdox.cloud.document.application.DocumentGenerationProperties;
import com.archdox.cloud.document.application.DocumentDeliveryProperties;
import com.archdox.cloud.documentai.application.DocumentAiReviewProperties;
import com.archdox.cloud.legal.application.LegalSyncProperties;
import com.archdox.cloud.photo.application.PhotoDerivativeProperties;
import com.archdox.cloud.photo.application.PhotoPickupProperties;
import com.archdox.cloud.platformops.application.PlatformOpsDetectionProperties;
import com.archdox.cloud.worker.ArchDoxWorkerRuntimeProperties;
import io.github.parkkevinsb.bloom.EventBus;
import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.bloom.BloomEventBus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArchDoxRuntimeConfiguration {
    public static final String DOCUMENT_IO_WORKER = "document-io";
    public static final String DOCUMENT_REVIEW_WORKER = "document-review";
    public static final String REPORT_PREFLIGHT_REVIEW_WORKER = "report-preflight-review";
    public static final String AI_HARNESS_WORKER = "ai-harness";
    public static final String MONITORING_WORKER = "monitoring";
    public static final String PLATFORM_OPS_WORKER = "platform-ops";
    public static final String ARCHDOX_WORKER_SERVICE_WORKER = "archdox-worker";
    public static final String LEGAL_SYNC_WORKER = "legal-sync";

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
            DocumentDeliveryProperties documentDeliveryProperties,
            DocumentAiReviewProperties documentAiReviewProperties,
            ArchDoxAgentConnectionHealthProperties agentConnectionHealthProperties,
            PlatformOpsDetectionProperties platformOpsDetectionProperties,
            ArchDoxWorkerRuntimeProperties archDoxWorkerRuntimeProperties,
            LegalSyncProperties legalSyncProperties,
            List<FlowerListener> flowerListeners
    ) {
        var builder = Engine.builder()
                .eventBus(BloomEventBus.wrap(eventBus))
                .worker(Worker.builder(DOCUMENT_IO_WORKER)
                        .intervalMillis(minPositive(
                                photoProperties.safeWorkerIntervalMs(),
                                photoPickupProperties.safeWorkerIntervalMs(),
                                documentProperties.safeWorkerIntervalMs(),
                                documentDeliveryProperties.safeWorkerIntervalMs()))
                        .build())
                .worker(Worker.builder(DOCUMENT_REVIEW_WORKER)
                        .intervalMillis(documentAiReviewProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(REPORT_PREFLIGHT_REVIEW_WORKER)
                        .intervalMillis(documentAiReviewProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(AI_HARNESS_WORKER)
                        .intervalMillis(documentAiReviewProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(MONITORING_WORKER)
                        .intervalMillis(agentConnectionHealthProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(PLATFORM_OPS_WORKER)
                        .intervalMillis(platformOpsDetectionProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(ARCHDOX_WORKER_SERVICE_WORKER)
                        .intervalMillis(archDoxWorkerRuntimeProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(LEGAL_SYNC_WORKER)
                        .intervalMillis(legalSyncProperties.safeWorkerIntervalMs())
                        .build());
        flowerListeners.forEach(builder::listener);
        return builder.build();
    }

    private static long minPositive(long first, long... rest) {
        var result = Math.max(1L, first);
        for (var value : rest) {
            result = Math.min(result, Math.max(1L, value));
        }
        return result;
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

    @Bean(destroyMethod = "shutdown")
    ExecutorService legalSyncFetchExecutor(LegalSyncProperties properties) {
        var sequence = new AtomicInteger();
        var threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                var thread = new Thread(runnable, "legal-sync-fetch-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        return new ThreadPoolExecutor(
                properties.safeFetchExecutorThreads(),
                properties.safeFetchExecutorThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.safeFetchExecutorQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService archDoxWorkerActionExecutor(ArchDoxWorkerRuntimeProperties properties) {
        var sequence = new AtomicInteger();
        var threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                var thread = new Thread(runnable, "archdox-worker-action-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        return new ThreadPoolExecutor(
                properties.safeActionExecutorThreads(),
                properties.safeActionExecutorThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.safeActionExecutorQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }
}

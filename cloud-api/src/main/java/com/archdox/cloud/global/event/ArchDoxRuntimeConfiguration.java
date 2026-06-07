package com.archdox.cloud.global.event;

import com.archdox.cloud.agent.application.ArchDoxAgentConnectionHealthProperties;
import com.archdox.cloud.document.application.DocumentGenerationProperties;
import com.archdox.cloud.document.application.DocumentDeliveryProperties;
import com.archdox.cloud.documentai.application.DocumentAiReviewProperties;
import com.archdox.cloud.legal.application.LegalDigestAiProperties;
import com.archdox.cloud.legal.application.LegalSyncProperties;
import com.archdox.cloud.photo.application.PhotoDerivativeProperties;
import com.archdox.cloud.photo.application.PhotoPickupProperties;
import com.archdox.cloud.platformops.application.PlatformOpsDetectionProperties;
import com.archdox.cloud.worker.ArchDoxWorkerRuntimeProperties;
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
    public static final String DOCUMENT_REVIEW_WORKER = "document-review";
    public static final String DOCUMENT_AI_REVIEW_WORKER = "document-ai-review";
    public static final String REPORT_PREFLIGHT_REVIEW_WORKER = "report-preflight-review";
    public static final String REPORT_PREFLIGHT_AI_REVIEW_WORKER = "report-preflight-ai-review";
    public static final String MONITORING_WORKER = "monitoring";
    public static final String PLATFORM_OPS_WORKER = "platform-ops";
    public static final String PLATFORM_OPS_AI_WORKER = "platform-ops-ai";
    public static final String ARCHDOX_WORKER_SERVICE_WORKER = "archdox-worker";
    public static final String ARCHDOX_WORKER_PLANNER_AI_WORKER = "archdox-worker-planner-ai";
    public static final String LEGAL_SYNC_WORKER = "legal-sync";
    public static final String LEGAL_DIGEST_AI_WORKER = "legal-digest-ai";

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
            LegalDigestAiProperties legalDigestAiProperties
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
                .worker(Worker.builder(DOCUMENT_REVIEW_WORKER)
                        .intervalMillis(documentAiReviewProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(DOCUMENT_AI_REVIEW_WORKER)
                        .intervalMillis(documentAiReviewProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(REPORT_PREFLIGHT_REVIEW_WORKER)
                        .intervalMillis(documentAiReviewProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(REPORT_PREFLIGHT_AI_REVIEW_WORKER)
                        .intervalMillis(documentAiReviewProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(MONITORING_WORKER)
                        .intervalMillis(agentConnectionHealthProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(PLATFORM_OPS_WORKER)
                        .intervalMillis(platformOpsDetectionProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(PLATFORM_OPS_AI_WORKER)
                        .intervalMillis(platformOpsDetectionProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(ARCHDOX_WORKER_SERVICE_WORKER)
                        .intervalMillis(archDoxWorkerRuntimeProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(ARCHDOX_WORKER_PLANNER_AI_WORKER)
                        .intervalMillis(archDoxWorkerRuntimeProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(LEGAL_SYNC_WORKER)
                        .intervalMillis(legalSyncProperties.safeWorkerIntervalMs())
                        .build())
                .worker(Worker.builder(LEGAL_DIGEST_AI_WORKER)
                        .intervalMillis(legalDigestAiProperties.safeWorkerIntervalMs())
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

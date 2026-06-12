package com.archdox.agent.cloud;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class AgentCommandExecutionManager {
    private final Lane photoPickup;
    private final Lane documentRender;
    private final Lane artifactDelivery;

    public AgentCommandExecutionManager(ArchDoxAgentProperties properties) {
        var execution = properties.getExecution();
        this.photoPickup = new Lane(
                "photo-pickup",
                execution.safePhotoPickupConcurrency(),
                execution.safePhotoPickupQueueCapacity());
        this.documentRender = new Lane(
                "document-render",
                execution.safeDocumentRenderConcurrency(),
                execution.safeDocumentRenderQueueCapacity());
        this.artifactDelivery = new Lane(
                "artifact-delivery",
                execution.safeArtifactDeliveryConcurrency(),
                execution.safeArtifactDeliveryQueueCapacity());
    }

    public boolean submitPhotoPickup(Runnable command) {
        return photoPickup.submit(command);
    }

    public boolean submitDocumentRender(Runnable command) {
        return documentRender.submit(command);
    }

    public boolean submitArtifactDelivery(Runnable command) {
        return artifactDelivery.submit(command);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                photoPickup.activeCount(),
                photoPickup.queuedCount(),
                documentRender.activeCount(),
                documentRender.queuedCount(),
                artifactDelivery.activeCount(),
                artifactDelivery.queuedCount());
    }

    @PreDestroy
    public void shutdown() {
        photoPickup.shutdown();
        documentRender.shutdown();
        artifactDelivery.shutdown();
    }

    public record Snapshot(
            int activePhotoPickupJobs,
            int queuedPhotoPickupJobs,
            int activeDocumentRenderJobs,
            int queuedDocumentRenderJobs,
            int activeArtifactDeliveryJobs,
            int queuedArtifactDeliveryJobs
    ) {
        public int pendingJobs() {
            return activePhotoPickupJobs
                    + queuedPhotoPickupJobs
                    + activeDocumentRenderJobs
                    + queuedDocumentRenderJobs
                    + activeArtifactDeliveryJobs
                    + queuedArtifactDeliveryJobs;
        }
    }

    private static final class Lane {
        private final ThreadPoolExecutor executor;

        private Lane(String name, int concurrency, int queueCapacity) {
            this.executor = new ThreadPoolExecutor(
                    concurrency,
                    concurrency,
                    0L,
                    TimeUnit.MILLISECONDS,
                    queue(queueCapacity),
                    threadFactory(name),
                    new ThreadPoolExecutor.AbortPolicy());
        }

        private boolean submit(Runnable command) {
            if (command == null) {
                return false;
            }
            try {
                executor.execute(command);
                return true;
            } catch (RejectedExecutionException ex) {
                return false;
            }
        }

        private int activeCount() {
            return executor.getActiveCount();
        }

        private int queuedCount() {
            return executor.getQueue().size();
        }

        private void shutdown() {
            executor.shutdownNow();
        }

        private static BlockingQueue<Runnable> queue(int queueCapacity) {
            return queueCapacity <= 0
                    ? new SynchronousQueue<>()
                    : new ArrayBlockingQueue<>(queueCapacity);
        }

        private static ThreadFactory threadFactory(String name) {
            var sequence = new AtomicInteger();
            return runnable -> {
                var thread = new Thread(runnable, "archdox-agent-" + name + "-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            };
        }
    }
}

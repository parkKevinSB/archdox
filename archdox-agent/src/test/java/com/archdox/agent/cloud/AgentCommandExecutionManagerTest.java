package com.archdox.agent.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AgentCommandExecutionManagerTest {
    @Test
    void documentRenderLaneLimitsConcurrencyAndQueueCapacity() throws Exception {
        var properties = new ArchDoxAgentProperties();
        properties.getExecution().setDocumentRenderConcurrency(1);
        properties.getExecution().setDocumentRenderQueueCapacity(1);

        var manager = new AgentCommandExecutionManager(properties);
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondCompleted = new CountDownLatch(1);
        try {
            assertTrue(manager.submitDocumentRender(() -> {
                firstStarted.countDown();
                await(releaseFirst);
            }));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            assertTrue(manager.submitDocumentRender(secondCompleted::countDown));
            assertFalse(manager.submitDocumentRender(() -> {
            }));

            var snapshot = manager.snapshot();
            assertEquals(1, snapshot.activeDocumentRenderJobs());
            assertEquals(1, snapshot.queuedDocumentRenderJobs());

            releaseFirst.countDown();
            assertTrue(secondCompleted.await(2, TimeUnit.SECONDS));
        } finally {
            manager.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}

package com.archdox.cloud.photo.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.archdox.cloud.photo.application.PhotoDerivativeProperties;
import com.archdox.cloud.photo.application.PhotoDerivativeService;
import com.archdox.cloud.photo.event.PhotoDerivativeGenerationFailed;
import com.archdox.cloud.photo.event.PhotoDerivativesGenerated;
import com.archdox.cloud.photo.event.PhotoUploadConfirmed;
import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.bloom.BloomEventBus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class PhotoDerivativeFlowFactoryTest {
    @Test
    void runsDerivativeStepsAndPublishesCompletionEvent() throws Exception {
        var service = mock(PhotoDerivativeService.class);
        var event = event();
        var properties = properties();
        var published = new ArrayList<PhotoDerivativesGenerated>();
        var bloom = LocalEventBus.create();
        bloom.subscribe(PhotoDerivativesGenerated.class, published::add);
        var worker = Worker.builder("test").build();
        var engine = Engine.builder()
                .eventBus(BloomEventBus.wrap(bloom))
                .worker(worker)
                .build();
        engine.attach();

        worker.submit(new PhotoDerivativeFlowFactory(service, Runnable::run, properties).create(event));
        tick(worker, 8);

        verify(service).prepare(10L, 100L);
        verify(service).generateWorking(10L, 100L);
        verify(service).generateThumbnail(10L, 100L);
        assertEquals(1, published.size());
        assertEquals(100L, published.get(0).photoId());
    }

    @Test
    void retriesFailedStepAfterBackoffBeforeContinuing() throws Exception {
        var service = mock(PhotoDerivativeService.class);
        doThrow(new IOException("temporary"))
                .doNothing()
                .when(service)
                .generateWorking(10L, 100L);
        var clock = new ManualClock();
        var properties = properties();
        properties.setMaxAttempts(2);
        properties.setRetryBaseDelayMs(50);
        properties.setRetryMaxDelayMs(50);
        var completed = new ArrayList<PhotoDerivativesGenerated>();
        var published = new ArrayList<PhotoDerivativeGenerationFailed>();
        var bloom = LocalEventBus.create();
        bloom.subscribe(PhotoDerivativesGenerated.class, completed::add);
        bloom.subscribe(PhotoDerivativeGenerationFailed.class, published::add);
        var worker = Worker.builder("test").build();
        var engine = Engine.builder()
                .clock(clock)
                .eventBus(BloomEventBus.wrap(bloom))
                .worker(worker)
                .build();
        engine.attach();

        worker.submit(new PhotoDerivativeFlowFactory(service, Runnable::run, properties).create(event()));
        tick(worker, 4);

        var snapshot = worker.snapshot().get(0);
        assertEquals("generate-working", snapshot.currentStepId());
        assertEquals(20, snapshot.currentStepNo());

        clock.advance(50);
        tick(worker, 7);

        verify(service, times(2)).generateWorking(10L, 100L);
        assertEquals(1, completed.size());
        assertEquals(100L, completed.get(0).photoId());
        assertTrue(published.isEmpty());
    }

    @Test
    void publishesFailureEventAfterRetryBudgetIsExhausted() throws Exception {
        var service = mock(PhotoDerivativeService.class);
        doThrow(new IOException("bad source")).when(service).generateWorking(10L, 100L);
        var clock = new ManualClock();
        var properties = properties();
        properties.setMaxAttempts(2);
        properties.setRetryBaseDelayMs(50);
        properties.setRetryMaxDelayMs(50);
        var published = new ArrayList<PhotoDerivativeGenerationFailed>();
        var bloom = LocalEventBus.create();
        bloom.subscribe(PhotoDerivativeGenerationFailed.class, published::add);
        var worker = Worker.builder("test").build();
        var engine = Engine.builder()
                .clock(clock)
                .eventBus(BloomEventBus.wrap(bloom))
                .worker(worker)
                .build();
        engine.attach();

        worker.submit(new PhotoDerivativeFlowFactory(service, Runnable::run, properties).create(event()));
        tick(worker, 4);
        clock.advance(50);
        tick(worker, 3);

        verify(service, times(2)).generateWorking(10L, 100L);
        assertEquals(1, published.size());
        assertEquals(100L, published.get(0).photoId());
        assertEquals("generate-working", published.get(0).stepId());
        assertEquals(2, published.get(0).attempt());
    }

    private PhotoUploadConfirmed event() {
        return new PhotoUploadConfirmed(10L, 100L, 1000L, 200L, OffsetDateTime.now());
    }

    private PhotoDerivativeProperties properties() {
        var properties = new PhotoDerivativeProperties();
        properties.setMaxAttempts(3);
        properties.setRetryBaseDelayMs(10);
        properties.setRetryMaxDelayMs(10);
        properties.setStepTimeoutMs(1000);
        return properties;
    }

    private void tick(Worker worker, int count) {
        for (int i = 0; i < count; i++) {
            worker.tickOnce();
        }
    }
}

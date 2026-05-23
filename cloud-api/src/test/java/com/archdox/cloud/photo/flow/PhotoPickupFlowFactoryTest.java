package com.archdox.cloud.photo.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.photo.application.PhotoPickupProperties;
import com.archdox.cloud.photo.application.PhotoPickupService;
import com.archdox.cloud.photo.event.PhotoOriginalPickupCompleted;
import com.archdox.cloud.photo.event.PhotoOriginalPickupFailed;
import com.archdox.cloud.photo.event.PhotoPickupCommandAckedEvent;
import com.archdox.cloud.photo.event.PhotoPickupCommandCompletedEvent;
import com.archdox.cloud.photo.event.PhotoPickupCommandFailedEvent;
import com.archdox.cloud.photo.event.PhotoPickupRequested;
import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.bloom.BloomEventBus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PhotoPickupFlowFactoryTest {
    @Test
    void waitsForAckAndCompletionEventsBeforeCompletingPickup() {
        var pickupService = mock(PhotoPickupService.class);
        var commandService = mock(ArchDoxAgentCommandService.class);
        var result = Map.<String, Object>of(
                "photoId", 100L,
                "agentOriginalStorageRef", "reports/100/original.jpg",
                "deleteTemporaryOriginal", true);
        when(pickupService.requiresPickup(10L, 100L)).thenReturn(true);
        when(commandService.enqueuePhotoPickup(10L, 100L, 1, 3)).thenReturn(Optional.of(99L));
        var bloom = LocalEventBus.create();
        var completed = new ArrayList<PhotoOriginalPickupCompleted>();
        bloom.subscribe(PhotoOriginalPickupCompleted.class, completed::add);
        var worker = workerWith(bloom);

        worker.submit(new PhotoPickupFlowFactory(pickupService, commandService, properties()).create(event()));
        tick(worker, 4);

        verify(commandService).enqueuePhotoPickup(10L, 100L, 1, 3);

        bloom.publish(new PhotoPickupCommandAckedEvent(10L, 100L, 99L, OffsetDateTime.now()));
        tick(worker, 3);

        bloom.publish(new PhotoPickupCommandCompletedEvent(10L, 100L, 99L, result, OffsetDateTime.now()));
        tick(worker, 3);

        verify(pickupService).completeFromAgent(10L, 100L, result);
        assertEquals(1, completed.size());
        assertEquals(99L, completed.get(0).commandId());
    }

    @Test
    void retriesFailedCommandAfterFlowerBackoff() {
        var pickupService = mock(PhotoPickupService.class);
        var commandService = mock(ArchDoxAgentCommandService.class);
        when(pickupService.requiresPickup(10L, 100L)).thenReturn(true);
        when(commandService.enqueuePhotoPickup(10L, 100L, 1, 2)).thenReturn(Optional.of(99L));
        when(commandService.enqueuePhotoPickup(10L, 100L, 2, 2)).thenReturn(Optional.of(100L));
        var clock = new ManualClock();
        var bloom = LocalEventBus.create();
        var properties = properties();
        properties.setMaxAttempts(2);
        properties.setRetryBaseDelayMs(50);
        properties.setRetryMaxDelayMs(50);
        var worker = workerWith(bloom, clock);

        worker.submit(new PhotoPickupFlowFactory(pickupService, commandService, properties).create(event()));
        tick(worker, 4);
        bloom.publish(new PhotoPickupCommandFailedEvent(10L, 100L, 99L, "temporary", Map.of(), OffsetDateTime.now()));
        tick(worker, 3);

        clock.advance(50);
        tick(worker, 4);

        verify(commandService).enqueuePhotoPickup(10L, 100L, 1, 2);
        verify(commandService).enqueuePhotoPickup(10L, 100L, 2, 2);
    }

    @Test
    void marksPhotoPickupFailedAfterRetryBudgetIsExhausted() {
        var pickupService = mock(PhotoPickupService.class);
        var commandService = mock(ArchDoxAgentCommandService.class);
        when(pickupService.requiresPickup(10L, 100L)).thenReturn(true);
        when(commandService.enqueuePhotoPickup(10L, 100L, 1, 1)).thenReturn(Optional.of(99L));
        var bloom = LocalEventBus.create();
        var failed = new ArrayList<PhotoOriginalPickupFailed>();
        bloom.subscribe(PhotoOriginalPickupFailed.class, failed::add);
        var properties = properties();
        properties.setMaxAttempts(1);
        var worker = workerWith(bloom);

        worker.submit(new PhotoPickupFlowFactory(pickupService, commandService, properties).create(event()));
        tick(worker, 4);
        bloom.publish(new PhotoPickupCommandFailedEvent(10L, 100L, 99L, "NAS unavailable", Map.of(), OffsetDateTime.now()));
        tick(worker, 3);

        verify(pickupService).markFailed(10L, 100L, "NAS unavailable");
        assertEquals(1, failed.size());
        assertEquals(1, failed.get(0).attempt());
    }

    private Worker workerWith(LocalEventBus bloom) {
        return workerWith(bloom, new ManualClock());
    }

    private Worker workerWith(LocalEventBus bloom, ManualClock clock) {
        var worker = Worker.builder("test").build();
        var engine = Engine.builder()
                .clock(clock)
                .eventBus(BloomEventBus.wrap(bloom))
                .worker(worker)
                .build();
        engine.attach();
        return worker;
    }

    private PhotoPickupRequested event() {
        return new PhotoPickupRequested(10L, 100L, 1000L, 200L, OffsetDateTime.now());
    }

    private PhotoPickupProperties properties() {
        var properties = new PhotoPickupProperties();
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

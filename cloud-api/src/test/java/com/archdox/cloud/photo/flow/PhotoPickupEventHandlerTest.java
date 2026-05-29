package com.archdox.cloud.photo.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.photo.application.PhotoPickupService;
import com.archdox.cloud.photo.event.PhotoDerivativesGenerated;
import com.archdox.cloud.photo.event.PhotoPickupRequested;
import io.github.parkkevinsb.flower.core.flow.Flow;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PhotoPickupEventHandlerTest {
    @Test
    void submitsPickupAfterDerivativesAreGenerated() {
        var pickupService = mock(PhotoPickupService.class);
        var flowFactory = mock(PhotoPickupFlowFactory.class);
        var worker = mock(PhotoPickupWorker.class);
        var flow = mock(Flow.class);
        when(pickupService.requiresPickup(10L, 100L)).thenReturn(true);
        when(flowFactory.create(any(PhotoPickupRequested.class))).thenReturn(flow);

        var occurredAt = OffsetDateTime.now();
        new PhotoPickupEventHandler(pickupService, flowFactory, worker)
                .onPhotoDerivativesGenerated(new PhotoDerivativesGenerated(10L, 100L, occurredAt));

        var captor = ArgumentCaptor.forClass(PhotoPickupRequested.class);
        verify(flowFactory).create(captor.capture());
        verify(worker).submit(flow);
        assertEquals(10L, captor.getValue().officeId());
        assertEquals(100L, captor.getValue().photoId());
        assertEquals(occurredAt, captor.getValue().requestedAt());
    }

    @Test
    void doesNotSubmitPickupWhenPhotoNoLongerRequiresPickup() {
        var pickupService = mock(PhotoPickupService.class);
        var flowFactory = mock(PhotoPickupFlowFactory.class);
        var worker = mock(PhotoPickupWorker.class);
        when(pickupService.requiresPickup(10L, 100L)).thenReturn(false);

        new PhotoPickupEventHandler(pickupService, flowFactory, worker)
                .onPhotoDerivativesGenerated(new PhotoDerivativesGenerated(10L, 100L, OffsetDateTime.now()));

        verify(flowFactory, never()).create(any());
        verify(worker, never()).submit(any());
    }
}

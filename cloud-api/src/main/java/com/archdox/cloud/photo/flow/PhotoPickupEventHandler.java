package com.archdox.cloud.photo.flow;

import com.archdox.cloud.photo.application.PhotoPickupService;
import com.archdox.cloud.photo.event.PhotoDerivativesGenerated;
import com.archdox.cloud.photo.event.PhotoPickupRequested;
import io.github.parkkevinsb.bloom.spring.Subscribe;
import org.springframework.stereotype.Component;

@Component
public class PhotoPickupEventHandler {
    private final PhotoPickupService photoPickupService;
    private final PhotoPickupFlowFactory flowFactory;
    private final PhotoPickupWorker worker;

    public PhotoPickupEventHandler(
            PhotoPickupService photoPickupService,
            PhotoPickupFlowFactory flowFactory,
            PhotoPickupWorker worker
    ) {
        this.photoPickupService = photoPickupService;
        this.flowFactory = flowFactory;
        this.worker = worker;
    }

    @Subscribe
    public void onPhotoDerivativesGenerated(PhotoDerivativesGenerated event) {
        if (!photoPickupService.requiresPickup(event.officeId(), event.photoId())) {
            return;
        }
        worker.submit(flowFactory.create(new PhotoPickupRequested(
                event.officeId(),
                event.photoId(),
                null,
                null,
                event.occurredAt())));
    }
}

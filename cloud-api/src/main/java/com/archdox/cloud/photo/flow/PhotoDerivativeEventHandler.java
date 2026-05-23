package com.archdox.cloud.photo.flow;

import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import com.archdox.cloud.photo.event.PhotoUploadConfirmed;
import io.github.parkkevinsb.bloom.spring.Subscribe;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import org.springframework.stereotype.Component;

@Component
public class PhotoDerivativeEventHandler {
    private final Engine engine;
    private final PhotoDerivativeFlowFactory flowFactory;

    public PhotoDerivativeEventHandler(
            Engine engine,
            PhotoDerivativeFlowFactory flowFactory
    ) {
        this.engine = engine;
        this.flowFactory = flowFactory;
    }

    @Subscribe
    public void onPhotoUploadConfirmed(PhotoUploadConfirmed event) {
        engine.worker(ArchDoxRuntimeConfiguration.PHOTO_DERIVATIVE_WORKER)
                .submit(flowFactory.create(event), DuplicatePolicy.REPLACE);
    }
}

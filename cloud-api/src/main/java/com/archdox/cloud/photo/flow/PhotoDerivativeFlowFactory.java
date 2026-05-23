package com.archdox.cloud.photo.flow;

import com.archdox.cloud.photo.application.PhotoDerivativeProperties;
import com.archdox.cloud.photo.application.PhotoDerivativeService;
import com.archdox.cloud.photo.event.PhotoUploadConfirmed;
import com.archdox.cloud.photo.flow.step.FinalizePhotoDerivativesStep;
import com.archdox.cloud.photo.flow.step.GenerateThumbnailPhotoStep;
import com.archdox.cloud.photo.flow.step.GenerateWorkingPhotoStep;
import com.archdox.cloud.photo.flow.step.PreparePhotoDerivativeSourceStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class PhotoDerivativeFlowFactory {
    public static final String FLOW_TYPE = "photo-derivative-generation";

    private final PhotoDerivativeService derivativeService;
    private final Executor photoDerivativeExecutor;
    private final PhotoDerivativeProperties properties;

    public PhotoDerivativeFlowFactory(
            PhotoDerivativeService derivativeService,
            @Qualifier("photoDerivativeExecutor") Executor photoDerivativeExecutor,
            PhotoDerivativeProperties properties
    ) {
        this.derivativeService = derivativeService;
        this.photoDerivativeExecutor = photoDerivativeExecutor;
        this.properties = properties;
    }

    public Flow create(PhotoUploadConfirmed event) {
        return Flow.builder(FLOW_TYPE, "photo:" + event.photoId())
                .step("prepare-source", new PreparePhotoDerivativeSourceStep(
                        derivativeService, event, photoDerivativeExecutor, properties))
                .step("generate-working", new GenerateWorkingPhotoStep(
                        derivativeService, event, photoDerivativeExecutor, properties))
                .step("generate-thumbnail", new GenerateThumbnailPhotoStep(
                        derivativeService, event, photoDerivativeExecutor, properties))
                .step("finalize", new FinalizePhotoDerivativesStep(event))
                .build();
    }
}

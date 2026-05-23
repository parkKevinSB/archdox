package com.archdox.cloud.photo.flow;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.photo.application.PhotoPickupProperties;
import com.archdox.cloud.photo.application.PhotoPickupService;
import com.archdox.cloud.photo.event.PhotoPickupRequested;
import com.archdox.cloud.photo.flow.step.ArchDoxAgentPhotoPickupStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class PhotoPickupFlowFactory {
    private final PhotoPickupService photoPickupService;
    private final ArchDoxAgentCommandService commandService;
    private final PhotoPickupProperties properties;

    public PhotoPickupFlowFactory(
            PhotoPickupService photoPickupService,
            ArchDoxAgentCommandService commandService,
            PhotoPickupProperties properties
    ) {
        this.photoPickupService = photoPickupService;
        this.commandService = commandService;
        this.properties = properties;
    }

    public Flow create(PhotoPickupRequested event) {
        return Flow.builder("photo-pickup", "photo:" + event.photoId())
                .step("archdox-agent-photo-pickup", new ArchDoxAgentPhotoPickupStep(
                        photoPickupService,
                        commandService,
                        event,
                        properties))
                .build();
    }
}

package com.archdox.cloud.document.flow;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.document.application.DocumentDeliveryProperties;
import com.archdox.cloud.document.application.DocumentDeliveryService;
import com.archdox.cloud.document.event.DocumentDeliveryRequested;
import com.archdox.cloud.document.flow.step.ArchDoxAgentDocumentDeliveryStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class DocumentDeliveryFlowFactory {
    public static final String FLOW_TYPE = "document-delivery";

    private final DocumentDeliveryService deliveryService;
    private final ArchDoxAgentCommandService commandService;
    private final DocumentDeliveryProperties properties;

    public DocumentDeliveryFlowFactory(
            DocumentDeliveryService deliveryService,
            ArchDoxAgentCommandService commandService,
            DocumentDeliveryProperties properties
    ) {
        this.deliveryService = deliveryService;
        this.commandService = commandService;
        this.properties = properties;
    }

    public Flow create(DocumentDeliveryRequested event) {
        return Flow.builder(FLOW_TYPE, "delivery:" + event.deliveryRequestId())
                .step("upload-archdox-agent-artifact", new ArchDoxAgentDocumentDeliveryStep(
                        deliveryService,
                        commandService,
                        event,
                        properties))
                .build();
    }
}

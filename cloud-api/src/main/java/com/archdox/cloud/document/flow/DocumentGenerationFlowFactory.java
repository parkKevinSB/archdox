package com.archdox.cloud.document.flow;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.document.application.DocumentGenerationProperties;
import com.archdox.cloud.document.application.DocumentJobService;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.document.event.DocumentGenerationRequested;
import com.archdox.cloud.document.flow.step.ArchDoxAgentDocumentRenderStep;
import com.archdox.cloud.document.flow.step.RenderCloudDocumentStep;
import com.archdox.cloud.document.flow.step.ValidateDocumentJobStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class DocumentGenerationFlowFactory {
    public static final String FLOW_TYPE = "document-generation";

    private final DocumentJobService documentJobService;
    private final ArchDoxAgentCommandService archDoxAgentCommandService;
    private final Executor documentGenerationExecutor;
    private final DocumentGenerationProperties properties;

    public DocumentGenerationFlowFactory(
            DocumentJobService documentJobService,
            ArchDoxAgentCommandService archDoxAgentCommandService,
            @Qualifier("documentGenerationExecutor") Executor documentGenerationExecutor,
            DocumentGenerationProperties properties
    ) {
        this.documentJobService = documentJobService;
        this.archDoxAgentCommandService = archDoxAgentCommandService;
        this.documentGenerationExecutor = documentGenerationExecutor;
        this.properties = properties;
    }

    public Flow create(DocumentGenerationRequested event) {
        var builder = Flow.builder(FLOW_TYPE, "job:" + event.documentJobId())
                .step("validate-job", new ValidateDocumentJobStep(
                        documentJobService, event, documentGenerationExecutor, properties));
        if (event.workerType() == DocumentWorkerType.ARCHDOX_AGENT) {
            return builder
                    .step("render-archdox-agent-document", new ArchDoxAgentDocumentRenderStep(
                            documentJobService, archDoxAgentCommandService, event, properties))
                    .build();
        }
        return builder
                .step("render-cloud-document", new RenderCloudDocumentStep(
                        documentJobService, event, documentGenerationExecutor, properties))
                .build();
    }
}

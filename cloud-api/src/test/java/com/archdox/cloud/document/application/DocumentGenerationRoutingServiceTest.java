package com.archdox.cloud.document.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.document.OutputFormat;
import org.junit.jupiter.api.Test;

class DocumentGenerationRoutingServiceTest {
    @Test
    void routesDocxToCloudWhenNoAgentIsAvailable() {
        var service = service(false, false);

        assertEquals(DocumentWorkerType.CLOUD, service.route(10L, OutputFormat.DOCX, null));
    }

    @Test
    void routesPdfToAgentWhenAgentSupportsItAndCloudConverterIsDisabled() {
        var service = service(true, false);

        assertEquals(DocumentWorkerType.ARCHDOX_AGENT, service.route(10L, OutputFormat.PDF, null));
    }

    @Test
    void routesPdfToCloudWhenLibreOfficeIsEnabledAndNoAgentIsAvailable() {
        var service = service(false, true);

        assertEquals(DocumentWorkerType.CLOUD, service.route(10L, OutputFormat.PDF, null));
    }

    @Test
    void rejectsPdfWhenNoWorkerSupportsIt() {
        var service = service(false, false);

        var error = assertThrows(BadRequestException.class, () -> service.route(10L, OutputFormat.PDF, null));

        assertEquals("DOCUMENT_WORKER_UNAVAILABLE", error.code());
    }

    @Test
    void rejectsExplicitCloudRouteWhenCloudCannotGenerateOutputFormat() {
        var service = service(true, false);

        var error = assertThrows(BadRequestException.class, () ->
                service.route(10L, OutputFormat.PDF, DocumentWorkerType.CLOUD));

        assertEquals("DOCUMENT_WORKER_UNSUPPORTED", error.code());
    }

    private DocumentGenerationRoutingService service(boolean hasAgent, boolean cloudLibreOfficeEnabled) {
        var commandService = mock(ArchDoxAgentCommandService.class);
        when(commandService.hasDocumentRenderTarget(10L, OutputFormat.DOCX)).thenReturn(hasAgent);
        when(commandService.hasDocumentRenderTarget(10L, OutputFormat.HTML)).thenReturn(hasAgent);
        when(commandService.hasDocumentRenderTarget(10L, OutputFormat.PDF)).thenReturn(hasAgent);
        when(commandService.hasDocumentRenderTarget(10L, OutputFormat.DOCX_AND_PDF)).thenReturn(hasAgent);
        when(commandService.hasDocumentRenderTarget(10L, OutputFormat.HTML_AND_PDF)).thenReturn(hasAgent);

        var exportProperties = new DocumentExportProperties();
        exportProperties.getLibreOffice().setEnabled(cloudLibreOfficeEnabled);
        return new DocumentGenerationRoutingService(commandService, exportProperties);
    }
}

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
    private static final String CHECKLIST_REPORT_TYPE = "CONSTRUCTION_SUPERVISION_CHECKLIST";

    @Test
    void routesToArchDoxAgentWhenCompatibleAgentIsAvailable() {
        var service = service(true);

        assertEquals(DocumentWorkerType.ARCHDOX_AGENT, service.route(10L, OutputFormat.DOCX, null));
    }

    @Test
    void rejectsDocxWhenNoAgentIsAvailable() {
        var service = service(false);

        var error = assertThrows(BadRequestException.class, () -> service.route(10L, OutputFormat.DOCX, null));

        assertEquals("DOCUMENT_WORKER_UNAVAILABLE", error.code());
    }

    @Test
    void rejectsPdfWhenNoAgentIsAvailable() {
        var service = service(false);

        var error = assertThrows(BadRequestException.class, () -> service.route(10L, OutputFormat.PDF, null));

        assertEquals("DOCUMENT_WORKER_UNAVAILABLE", error.code());
    }

    @Test
    void rejectsExplicitAgentRouteWhenNoAgentIsAvailable() {
        var service = service(false);

        var error = assertThrows(BadRequestException.class, () ->
                service.route(10L, OutputFormat.PDF, DocumentWorkerType.ARCHDOX_AGENT));

        assertEquals("DOCUMENT_WORKER_UNSUPPORTED", error.code());
    }

    @Test
    void routesChecklistDocxToCloudApiWithoutAgent() {
        var service = service(false);

        assertEquals(
                DocumentWorkerType.CLOUD_API,
                service.route(10L, CHECKLIST_REPORT_TYPE, OutputFormat.DOCX, null));
    }

    @Test
    void routesChecklistPdfToCloudApiWithoutAgent() {
        var service = service(false);

        assertEquals(
                DocumentWorkerType.CLOUD_API,
                service.route(10L, CHECKLIST_REPORT_TYPE, OutputFormat.PDF, null));
    }

    @Test
    void routesExplicitChecklistPdfToCloudApi() {
        var service = service(false);

        assertEquals(
                DocumentWorkerType.CLOUD_API,
                service.route(10L, CHECKLIST_REPORT_TYPE, OutputFormat.PDF, DocumentWorkerType.CLOUD_API));
    }

    @Test
    void rejectsExplicitCloudApiRouteForNonChecklistReport() {
        var service = service(false);

        var error = assertThrows(BadRequestException.class, () ->
                service.route(10L, "CONSTRUCTION_DAILY_SUPERVISION_LOG", OutputFormat.DOCX, DocumentWorkerType.CLOUD_API));

        assertEquals("DOCUMENT_WORKER_UNSUPPORTED", error.code());
    }

    private DocumentGenerationRoutingService service(boolean hasAgent) {
        var commandService = mock(ArchDoxAgentCommandService.class);
        when(commandService.hasDocumentRenderTarget(10L, OutputFormat.DOCX)).thenReturn(hasAgent);
        when(commandService.hasDocumentRenderTarget(10L, OutputFormat.HTML)).thenReturn(hasAgent);
        when(commandService.hasDocumentRenderTarget(10L, OutputFormat.PDF)).thenReturn(hasAgent);
        when(commandService.hasDocumentRenderTarget(10L, OutputFormat.DOCX_AND_PDF)).thenReturn(hasAgent);
        when(commandService.hasDocumentRenderTarget(10L, OutputFormat.HTML_AND_PDF)).thenReturn(hasAgent);

        return new DocumentGenerationRoutingService(commandService);
    }
}

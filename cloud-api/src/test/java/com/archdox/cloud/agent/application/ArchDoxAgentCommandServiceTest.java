package com.archdox.cloud.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommand;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommandStatus;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommandType;
import com.archdox.cloud.agent.domain.ArchDoxAgentDeploymentMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentSession;
import com.archdox.cloud.agent.domain.ArchDoxAgentSessionStatus;
import com.archdox.cloud.agent.domain.ArchDoxAgentStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentCommandRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentHeartbeatRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentSessionRepository;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.photo.application.PhotoPickupService;
import com.archdox.document.OutputFormat;
import io.github.parkkevinsb.bloom.EventBus;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ArchDoxAgentCommandServiceTest {
    @Test
    void enqueueDocumentRenderSelectsAgentThatSupportsRequestedOutputFormat() {
        var repositories = repositories();
        var sessionRegistry = mock(ArchDoxAgentSessionRegistry.class);
        var operationEvents = mock(OperationEventService.class);
        var service = service(repositories, sessionRegistry, operationEvents);
        var now = OffsetDateTime.now();
        var docxOnlyAgent = agent(
                11L,
                "docx-agent",
                Map.of("documentGeneration", true, "outputFormats", List.of("DOCX")),
                now);
        var pdfAgent = agent(
                22L,
                "pdf-agent",
                Map.of("documentGeneration", true, "outputFormats", List.of("DOCX", "PDF")),
                now.plusSeconds(1));
        when(repositories.sessionRepository.findByOfficeIdAndStatusOrderByLastSeenAtDesc(
                10L,
                ArchDoxAgentSessionStatus.ACTIVE))
                .thenReturn(List.of(
                        new ArchDoxAgentSession(docxOnlyAgent, "api-1", "ws-1", now),
                        new ArchDoxAgentSession(pdfAgent, "api-1", "ws-2", now.plusSeconds(1))));
        when(repositories.commandRepository.save(any(ArchDoxAgentCommand.class)))
                .thenAnswer(invocation -> {
                    var command = invocation.getArgument(0, ArchDoxAgentCommand.class);
                    ReflectionTestUtils.setField(command, "id", 56L);
                    return command;
                });
        when(sessionRegistry.send(eq(22L), any(AgentOutboundMessage.class))).thenReturn(true);
        var renderPayload = renderPayload(OutputFormat.DOCX_AND_PDF);

        var commandId = service.enqueueDocumentRender(10L, 700L, renderPayload, 2, 5);

        assertEquals(Optional.of(56L), commandId);
        var savedCommand = ArgumentCaptor.forClass(ArchDoxAgentCommand.class);
        verify(repositories.commandRepository).save(savedCommand.capture());
        assertEquals(pdfAgent, savedCommand.getValue().agent());
        assertEquals(ArchDoxAgentCommandType.GENERATE_DOCUMENT, savedCommand.getValue().commandType());
        assertEquals(ArchDoxAgentCommandStatus.DELIVERED, savedCommand.getValue().status());
        assertEquals("DOCX_AND_PDF", savedCommand.getValue().payloadJson().get("outputFormat"));
        assertEquals(2, savedCommand.getValue().payloadJson().get("attempt"));
        assertEquals(5, savedCommand.getValue().payloadJson().get("maxAttempts"));
        assertInstanceOf(OffsetDateTime.class, savedCommand.getValue().payloadJson().get("expiresAt"));

        var outboundMessage = ArgumentCaptor.forClass(AgentOutboundMessage.class);
        verify(sessionRegistry).send(eq(22L), outboundMessage.capture());
        assertEquals("COMMAND", outboundMessage.getValue().type());
        assertEquals(56L, outboundMessage.getValue().commandId());
        assertEquals(ArchDoxAgentCommandType.GENERATE_DOCUMENT, outboundMessage.getValue().commandType());
        assertEquals(savedCommand.getValue().payloadJson(), outboundMessage.getValue().payload());
        verify(operationEvents).record(
                eq(10L),
                any(),
                eq("AGENT_COMMAND_ENQUEUED"),
                eq("document-generation"),
                eq("document-job:700"),
                eq("DOCUMENT_JOB"),
                eq(700L),
                eq("GENERATE_DOCUMENT command enqueued."),
                any());
    }

    @Test
    void enqueueDocumentRenderDoesNotPersistWhenNoAgentSupportsOutputFormat() {
        var repositories = repositories();
        var service = service(
                repositories,
                mock(ArchDoxAgentSessionRegistry.class),
                mock(OperationEventService.class));
        var now = OffsetDateTime.now();
        var docxOnlyAgent = agent(
                11L,
                "docx-agent",
                Map.of("documentGeneration", true, "outputFormats", List.of("DOCX")),
                now);
        when(repositories.sessionRepository.findByOfficeIdAndStatusOrderByLastSeenAtDesc(
                10L,
                ArchDoxAgentSessionStatus.ACTIVE))
                .thenReturn(List.of(new ArchDoxAgentSession(docxOnlyAgent, "api-1", "ws-1", now)));
        when(repositories.agentRepository.findByOfficeIdAndStatusOrderByLastSeenAtDesc(
                10L,
                ArchDoxAgentStatus.ONLINE))
                .thenReturn(List.of(docxOnlyAgent));

        var commandId = service.enqueueDocumentRender(10L, 700L, renderPayload(OutputFormat.PDF), 1, 3);

        assertTrue(commandId.isEmpty());
        verify(repositories.commandRepository, never()).save(any());
    }

    private ArchDoxAgentCommandService service(
            Repositories repositories,
            ArchDoxAgentSessionRegistry sessionRegistry,
            OperationEventService operationEvents
    ) {
        var properties = new ArchDoxAgentProperties();
        properties.setCommandTtlMinutes(10);
        return new ArchDoxAgentCommandService(
                repositories.agentRepository,
                repositories.heartbeatRepository,
                repositories.commandRepository,
                repositories.sessionRepository,
                mock(OfficeRepository.class),
                mock(PhotoPickupService.class),
                sessionRegistry,
                properties,
                mock(EventBus.class),
                operationEvents);
    }

    private Repositories repositories() {
        return new Repositories(
                mock(ArchDoxAgentRepository.class),
                mock(ArchDoxAgentHeartbeatRepository.class),
                mock(ArchDoxAgentCommandRepository.class),
                mock(ArchDoxAgentSessionRepository.class));
    }

    private ArchDoxAgent agent(Long id, String agentCode, Map<String, Object> capabilities, OffsetDateTime now) {
        var agent = new ArchDoxAgent(
                10L,
                agentCode,
                ArchDoxAgentDeploymentMode.LOCAL_OFFICE,
                "1.0.0",
                capabilities,
                Map.of("storageType", "NAS"),
                now);
        ReflectionTestUtils.setField(agent, "id", id);
        return agent;
    }

    private Map<String, Object> renderPayload(OutputFormat outputFormat) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("documentJobId", 700L);
        payload.put("officeId", 10L);
        payload.put("reportId", 1000L);
        payload.put("projectId", 100L);
        payload.put("outputFormat", outputFormat.name());
        payload.put("inputSnapshot", Map.of("report", Map.of("title", "Safety inspection")));
        payload.put("template", Map.of(
                "templateCode", "INSPECTION_REPORT",
                "version", 1,
                "storageRef", "templates/default.docx"));
        payload.put("photos", List.of(Map.of(
                "photoId", "9881",
                "storageRef", "offices/10/reports/1000/photos/9881/working.jpg",
                "mimeType", "image/jpeg")));
        payload.put("resultStorageKind", "ARCHDOX_AGENT");
        return payload;
    }

    private record Repositories(
            ArchDoxAgentRepository agentRepository,
            ArchDoxAgentHeartbeatRepository heartbeatRepository,
            ArchDoxAgentCommandRepository commandRepository,
            ArchDoxAgentSessionRepository sessionRepository
    ) {
    }
}

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
import com.archdox.cloud.document.event.DocumentRenderCommandFailedEvent;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

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
        var savedCommandRef = new AtomicReference<ArchDoxAgentCommand>();
        when(repositories.commandRepository.save(any(ArchDoxAgentCommand.class)))
                .thenAnswer(invocation -> {
                    var command = invocation.getArgument(0, ArchDoxAgentCommand.class);
                    ReflectionTestUtils.setField(command, "id", 56L);
                    savedCommandRef.set(command);
                    return command;
                });
        when(repositories.commandRepository.findById(56L))
                .thenAnswer(invocation -> Optional.ofNullable(savedCommandRef.get()));
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

    @Test
    void enqueueDocumentRenderMarksLocalSessionStaleWhenNoOpenWebSocketExists() {
        var repositories = repositories();
        var sessionRegistry = mock(ArchDoxAgentSessionRegistry.class);
        var service = service(
                repositories,
                sessionRegistry,
                mock(OperationEventService.class));
        var now = OffsetDateTime.now();
        var agent = agent(
                22L,
                "pdf-agent",
                Map.of("documentGeneration", true, "outputFormats", List.of("DOCX", "PDF")),
                now);
        when(repositories.sessionRepository.findByOfficeIdAndStatusOrderByLastSeenAtDesc(
                10L,
                ArchDoxAgentSessionStatus.ACTIVE))
                .thenReturn(List.of(new ArchDoxAgentSession(agent, "cloud-api-local", "ws-stale", now)));
        var savedCommandRef = new AtomicReference<ArchDoxAgentCommand>();
        when(repositories.commandRepository.save(any(ArchDoxAgentCommand.class)))
                .thenAnswer(invocation -> {
                    var command = invocation.getArgument(0, ArchDoxAgentCommand.class);
                    ReflectionTestUtils.setField(command, "id", 57L);
                    savedCommandRef.set(command);
                    return command;
                });
        when(repositories.commandRepository.findById(57L))
                .thenAnswer(invocation -> Optional.ofNullable(savedCommandRef.get()));
        when(sessionRegistry.send(eq(22L), any(AgentOutboundMessage.class))).thenReturn(false);

        var commandId = service.enqueueDocumentRender(10L, 700L, renderPayload(OutputFormat.DOCX), 1, 3);

        assertEquals(Optional.of(57L), commandId);
        var savedCommand = ArgumentCaptor.forClass(ArchDoxAgentCommand.class);
        verify(repositories.commandRepository).save(savedCommand.capture());
        assertEquals(ArchDoxAgentCommandStatus.PENDING, savedCommand.getValue().status());
        verify(repositories.sessionRepository).markActiveSessionsDisconnectedForAgentAndApiInstance(
                eq(22L),
                eq("cloud-api-local"),
                eq(ArchDoxAgentSessionStatus.ACTIVE),
                eq(ArchDoxAgentSessionStatus.DISCONNECTED),
                any(),
                eq("No open WebSocket in API instance during command dispatch"));
    }

    @Test
    void disconnectFailsInFlightDocumentRenderCommandsWhenNoActiveSessionRemains() {
        var repositories = repositories();
        var eventBus = mock(EventBus.class);
        var service = service(
                repositories,
                mock(ArchDoxAgentSessionRegistry.class),
                mock(OperationEventService.class),
                eventBus);
        var now = OffsetDateTime.now();
        var agent = agent(
                22L,
                "office-main",
                Map.of("documentGeneration", true, "outputFormats", List.of("DOCX")),
                now);
        var command = new ArchDoxAgentCommand(
                agent,
                ArchDoxAgentCommandType.GENERATE_DOCUMENT,
                renderPayload(OutputFormat.DOCX),
                now,
                now.plusMinutes(10));
        ReflectionTestUtils.setField(command, "id", 91L);
        command.markDelivered(now.plusSeconds(1));
        command.ack(now.plusSeconds(2));
        when(repositories.sessionRepository.existsByAgentIdAndStatus(22L, ArchDoxAgentSessionStatus.ACTIVE))
                .thenReturn(false);
        when(repositories.agentRepository.findById(22L)).thenReturn(Optional.of(agent));
        when(repositories.commandRepository.findByAgentIdAndStatusInOrderByCreatedAtAsc(
                eq(22L),
                eq(List.of(
                        ArchDoxAgentCommandStatus.PENDING,
                        ArchDoxAgentCommandStatus.DELIVERED,
                        ArchDoxAgentCommandStatus.ACKED))))
                .thenReturn(List.of(command));

        service.disconnect(22L);

        assertEquals(ArchDoxAgentStatus.OFFLINE, agent.status());
        assertEquals(ArchDoxAgentCommandStatus.FAILED, command.status());
        assertEquals("ArchDox Agent disconnected before command completed", command.errorMessage());
        var eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventBus).publish(eventCaptor.capture());
        var event = assertInstanceOf(DocumentRenderCommandFailedEvent.class, eventCaptor.getValue());
        assertEquals(700L, event.documentJobId());
        assertEquals(91L, event.commandId());
        assertEquals("ARCHDOX_AGENT_DISCONNECTED", event.errorCode());
        assertEquals(false, event.retryable());
    }

    private ArchDoxAgentCommandService service(
            Repositories repositories,
            ArchDoxAgentSessionRegistry sessionRegistry,
            OperationEventService operationEvents
    ) {
        return service(repositories, sessionRegistry, operationEvents, mock(EventBus.class));
    }

    private ArchDoxAgentCommandService service(
            Repositories repositories,
            ArchDoxAgentSessionRegistry sessionRegistry,
            OperationEventService operationEvents,
            EventBus eventBus
    ) {
        var properties = new ArchDoxAgentProperties();
        properties.setCommandTtlMinutes(10);
        properties.setApiInstanceId("cloud-api-local");
        return new ArchDoxAgentCommandService(
                repositories.agentRepository,
                repositories.heartbeatRepository,
                repositories.commandRepository,
                repositories.sessionRepository,
                mock(OfficeRepository.class),
                mock(PhotoPickupService.class),
                sessionRegistry,
                properties,
                new ArchDoxAgentRuntimeCompatibilityService(properties),
                eventBus,
                operationEvents,
                transactionManager());
    }

    private PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }

    private Repositories repositories() {
        return new Repositories(
                mock(ArchDoxAgentRepository.class),
                mock(ArchDoxAgentHeartbeatRepository.class),
                mock(ArchDoxAgentCommandRepository.class),
                mock(ArchDoxAgentSessionRepository.class));
    }

    private ArchDoxAgent agent(Long id, String agentCode, Map<String, Object> capabilities, OffsetDateTime now) {
        var compatibleCapabilities = new LinkedHashMap<String, Object>(capabilities);
        compatibleCapabilities.put("compatibility", Map.of(
                "status", "OK",
                "commandAllowed", true,
                "updateRequired", false,
                "reason", "Test Agent runtime is compatible."));
        var agent = new ArchDoxAgent(
                10L,
                agentCode,
                ArchDoxAgentDeploymentMode.LOCAL_OFFICE,
                "1.0.0",
                compatibleCapabilities,
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

package com.archdox.agent.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.ContainerProvider;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class CloudAgentWebSocketClient extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(CloudAgentWebSocketClient.class);

    private final ArchDoxAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final ArchDoxAgentCapabilityProvider capabilityProvider;
    private final PhotoPickupCommandExecutor photoPickupCommandExecutor;
    private final DocumentRenderCommandExecutor documentRenderCommandExecutor;
    private final DocumentArtifactDeliveryCommandExecutor documentArtifactDeliveryCommandExecutor;
    private final Object sendLock = new Object();
    private WebSocketSession session;

    public CloudAgentWebSocketClient(
            ArchDoxAgentProperties properties,
            ObjectMapper objectMapper,
            ArchDoxAgentCapabilityProvider capabilityProvider,
            PhotoPickupCommandExecutor photoPickupCommandExecutor,
            DocumentRenderCommandExecutor documentRenderCommandExecutor,
            DocumentArtifactDeliveryCommandExecutor documentArtifactDeliveryCommandExecutor
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.capabilityProvider = capabilityProvider;
        this.photoPickupCommandExecutor = photoPickupCommandExecutor;
        this.documentRenderCommandExecutor = documentRenderCommandExecutor;
        this.documentArtifactDeliveryCommandExecutor = documentArtifactDeliveryCommandExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connectOnStartup() {
        if (!properties.isEnabled()) {
            log.info("ArchDox Agent WebSocket client is disabled");
            return;
        }
        connect();
    }

    @Scheduled(fixedDelayString = "${archdox.agent.heartbeat-interval-ms:30000}")
    public void heartbeat() {
        if (!properties.isEnabled() || session == null || !session.isOpen()) {
            return;
        }
        send(CloudOutboundMessage.heartbeat(properties));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.session = session;
        session.setTextMessageSizeLimit(properties.safeWebsocketMaxTextMessageBufferBytes());
        session.setBinaryMessageSizeLimit(properties.safeWebsocketMaxBinaryMessageBufferBytes());
        send(CloudOutboundMessage.hello(properties, capabilityProvider.capabilities()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("ArchDox Cloud WebSocket closed: {}", status);
        this.session = null;
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("ArchDox Cloud WebSocket transport error", exception);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        var inbound = objectMapper.readValue(message.getPayload(), CloudInboundMessage.class);
        if ("WELCOME".equals(inbound.type())) {
            log.info("Connected to ArchDox Cloud as ArchDox Agent {}", inbound.agentId());
            if (inbound.deviceSecret() != null && !inbound.deviceSecret().isBlank()) {
                properties.setAgentId(inbound.agentId());
                properties.setDeviceSecret(inbound.deviceSecret());
                properties.setAuthMode("DEVICE_SECRET");
                log.warn("Agent pairing issued a device secret. Store AGENT_ID={} and AGENT_DEVICE_SECRET securely, then remove AGENT_INSTALL_TOKEN.", inbound.agentId());
            }
            return;
        }
        if ("COMMAND".equals(inbound.type())) {
            handleCommand(inbound);
            return;
        }
        if ("ERROR".equals(inbound.type())) {
            log.warn("Cloud agent channel error: {}", inbound.message());
        }
    }

    private void connect() {
        try {
            var container = ContainerProvider.getWebSocketContainer();
            container.setDefaultMaxTextMessageBufferSize(properties.safeWebsocketMaxTextMessageBufferBytes());
            container.setDefaultMaxBinaryMessageBufferSize(properties.safeWebsocketMaxBinaryMessageBufferBytes());
            new StandardWebSocketClient(container)
                    .execute(this, properties.getCloudWsUrl())
                    .get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while connecting to ArchDox Cloud WebSocket", ex);
        } catch (ExecutionException ex) {
            log.warn("Failed to connect to ArchDox Cloud WebSocket", ex);
        }
    }

    private void handleCommand(CloudInboundMessage inbound) {
        log.info("Received ArchDox Agent command {} type {}", inbound.commandId(), inbound.commandType());
        send(CloudOutboundMessage.ack(inbound.commandId()));
        if ("PHOTO_PICKUP".equals(inbound.commandType())) {
            CompletableFuture.runAsync(() -> executePhotoPickup(inbound));
            return;
        }
        if ("GENERATE_DOCUMENT".equals(inbound.commandType()) || "DOCUMENT_RENDER".equals(inbound.commandType())) {
            CompletableFuture.runAsync(() -> executeDocumentRender(inbound));
            return;
        }
        if ("UPLOAD_DOCUMENT_ARTIFACT".equals(inbound.commandType())) {
            CompletableFuture.runAsync(() -> executeDocumentArtifactDelivery(inbound));
        }
    }

    private void executePhotoPickup(CloudInboundMessage inbound) {
        try {
            var result = photoPickupCommandExecutor.execute(inbound);
            send(CloudOutboundMessage.complete(inbound.commandId(), result));
            log.info("PHOTO_PICKUP command {} completed", inbound.commandId());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            send(CloudOutboundMessage.fail(inbound.commandId(), AgentCommandFailureClassifier.classify(
                    ex,
                    "PHOTO_PICKUP_FAILED")));
        } catch (Exception ex) {
            log.warn("PHOTO_PICKUP command {} failed", inbound.commandId(), ex);
            send(CloudOutboundMessage.fail(inbound.commandId(), AgentCommandFailureClassifier.classify(
                    ex,
                    "PHOTO_PICKUP_FAILED")));
        }
    }

    private void executeDocumentRender(CloudInboundMessage inbound) {
        try {
            var result = documentRenderCommandExecutor.execute(inbound);
            send(CloudOutboundMessage.complete(inbound.commandId(), result));
            log.info("GENERATE_DOCUMENT command {} completed", inbound.commandId());
        } catch (Exception ex) {
            log.warn("GENERATE_DOCUMENT command {} failed", inbound.commandId(), ex);
            send(CloudOutboundMessage.fail(inbound.commandId(), AgentCommandFailureClassifier.classify(
                    ex,
                    "DOCUMENT_RENDER_FAILED")));
        }
    }

    private void executeDocumentArtifactDelivery(CloudInboundMessage inbound) {
        try {
            var result = documentArtifactDeliveryCommandExecutor.execute(inbound);
            send(CloudOutboundMessage.complete(inbound.commandId(), result));
            log.info("UPLOAD_DOCUMENT_ARTIFACT command {} completed", inbound.commandId());
        } catch (Exception ex) {
            log.warn("UPLOAD_DOCUMENT_ARTIFACT command {} failed", inbound.commandId(), ex);
            send(CloudOutboundMessage.fail(inbound.commandId(), AgentCommandFailureClassifier.classify(
                    ex,
                    "DOCUMENT_ARTIFACT_DELIVERY_FAILED")));
        }
    }

    private void send(CloudOutboundMessage outbound) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            synchronized (sendLock) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(outbound)));
            }
        } catch (Exception ex) {
            log.warn("Failed to send Cloud WebSocket message", ex);
        }
    }
}

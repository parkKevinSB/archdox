package com.archdox.cloud.agent.api;

import com.archdox.cloud.agent.application.AgentInboundMessage;
import com.archdox.cloud.agent.application.AgentOutboundMessage;
import com.archdox.cloud.agent.application.ArchDoxAgentAuthenticationService;
import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.agent.application.ArchDoxAgentSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final ArchDoxAgentAuthenticationService authenticationService;
    private final ArchDoxAgentCommandService commandService;
    private final ArchDoxAgentSessionRegistry sessionRegistry;

    public AgentWebSocketHandler(
            ObjectMapper objectMapper,
            ArchDoxAgentAuthenticationService authenticationService,
            ArchDoxAgentCommandService commandService,
            ArchDoxAgentSessionRegistry sessionRegistry
    ) {
        this.objectMapper = objectMapper;
        this.authenticationService = authenticationService;
        this.commandService = commandService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        var inbound = objectMapper.readValue(message.getPayload(), AgentInboundMessage.class);
        try {
            switch (inbound.type()) {
                case "HELLO" -> handleHello(session, inbound);
                case "HEARTBEAT" -> commandService.heartbeat(requireAgent(session), inbound.toHeartbeat());
                case "ACK" -> commandService.ack(requireAgent(session), inbound.commandId());
                case "COMPLETE" -> commandService.complete(requireAgent(session), inbound.commandId(), inbound.result());
                case "FAIL" -> commandService.fail(requireAgent(session), inbound.commandId(), inbound.errorMessage(), inbound.result());
                default -> send(session, AgentOutboundMessage.error("Unsupported message type: " + inbound.type()));
            }
        } catch (Exception ex) {
            send(session, AgentOutboundMessage.error(ex.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var agentId = sessionRegistry.unregister(session, status.toString());
        commandService.disconnect(agentId);
    }

    private void handleHello(WebSocketSession session, AgentInboundMessage inbound) throws Exception {
        var connection = authenticationService.connect(inbound.toHello());
        var agent = connection.agent();
        sessionRegistry.register(agent, session);
        send(session, AgentOutboundMessage.welcome(agent.id(), connection.issuedDeviceSecret()));
        commandService.deliverPending(agent.id());
    }

    private Long requireAgent(WebSocketSession session) {
        var agentId = sessionRegistry.agentId(session);
        if (agentId == null) {
            throw new IllegalStateException("Agent must send HELLO before other messages");
        }
        sessionRegistry.touch(session);
        return agentId;
    }

    private void send(WebSocketSession session, AgentOutboundMessage message) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        }
    }
}

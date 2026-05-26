package com.archdox.cloud.agent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.archdox.cloud.agent.application.AgentOutboundMessage;
import com.archdox.cloud.agent.domain.ArchDoxAgentSession;
import com.archdox.cloud.agent.domain.ArchDoxAgentSessionStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ArchDoxAgentWebSocketAuthenticationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("archdox.agent.api-instance-id", () -> "ws-auth-test-api");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ArchDoxAgentSessionRepository sessionRepository;

    @LocalServerPort
    int port;

    @Test
    void installTokenHelloIssuesDeviceSecretAndDeviceSecretHelloReconnects() throws Exception {
        var user = signup();
        var installToken = issueInstallToken(user);

        var installWelcome = sendHello(Map.of(
                "type", "HELLO",
                "authMode", "INSTALL_TOKEN",
                "officeId", user.officeId(),
                "agentCode", "office-main",
                "installToken", installToken,
                "version", "0.0.1-test",
                "capabilities", Map.of("photoPickup", true, "nas", true)));

        assertEquals("WELCOME", installWelcome.type());
        assertEquals("INSTALL_TOKEN", installWelcome.authMode());
        assertNotNull(installWelcome.agentId());
        assertTrue(installWelcome.deviceSecret() != null && !installWelcome.deviceSecret().isBlank());

        var deviceWelcome = sendHello(Map.of(
                "type", "HELLO",
                "authMode", "DEVICE_SECRET",
                "agentId", installWelcome.agentId(),
                "deviceSecret", installWelcome.deviceSecret(),
                "version", "0.0.1-test",
                "capabilities", Map.of("photoPickup", true, "nas", true)));

        assertEquals("WELCOME", deviceWelcome.type());
        assertEquals("DEVICE_SECRET", deviceWelcome.authMode());
        assertEquals(installWelcome.agentId(), deviceWelcome.agentId());

        var sessions = waitForSessions(installWelcome.agentId(), 2);
        assertTrue(sessions.stream()
                .allMatch(session -> session.status() == ArchDoxAgentSessionStatus.DISCONNECTED));
        assertTrue(sessions.stream()
                .allMatch(session -> "ws-auth-test-api".equals(session.apiInstanceId())));
    }

    @Test
    void duplicateDeviceSecretHelloIsRejectedWhileExistingSessionIsActive() throws Exception {
        var user = signup();
        var installToken = issueInstallToken(user);
        var open = openHello(Map.of(
                "type", "HELLO",
                "authMode", "INSTALL_TOKEN",
                "officeId", user.officeId(),
                "agentCode", "office-main",
                "installToken", installToken,
                "version", "0.0.1-test",
                "capabilities", Map.of("photoPickup", true, "documentGeneration", true)));
        try {
            assertEquals("WELCOME", open.welcome().type());
            assertNotNull(open.welcome().agentId());

            var duplicate = sendHello(Map.of(
                    "type", "HELLO",
                    "authMode", "DEVICE_SECRET",
                    "agentId", open.welcome().agentId(),
                    "deviceSecret", open.welcome().deviceSecret(),
                    "version", "0.0.1-test",
                    "capabilities", Map.of("photoPickup", true, "documentGeneration", true)));

            assertEquals("ERROR", duplicate.type());
            assertEquals("ArchDox Agent is already connected", duplicate.message());
            var sessions = sessionRepository.findByAgentIdOrderByConnectedAtAsc(open.welcome().agentId());
            assertEquals(1, sessions.size());
            assertEquals(ArchDoxAgentSessionStatus.ACTIVE, sessions.get(0).status());
        } finally {
            if (open.session().isOpen()) {
                open.session().close(CloseStatus.NORMAL);
            }
        }
    }

    private AgentOutboundMessage sendHello(Map<String, Object> hello) throws Exception {
        var response = new CompletableFuture<AgentOutboundMessage>();
        var sessionRef = new CompletableFuture<WebSocketSession>();
        var client = new StandardWebSocketClient();
        var handler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessionRef.complete(session);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(hello)));
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                response.complete(objectMapper.readValue(message.getPayload(), AgentOutboundMessage.class));
                session.close(CloseStatus.NORMAL);
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                response.completeExceptionally(exception);
            }
        };

        client.execute(handler, "ws://localhost:" + port + "/agent/ws")
                .get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
        var outbound = response.get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
        var session = sessionRef.getNow(null);
        if (session != null && session.isOpen()) {
            session.close(CloseStatus.NORMAL);
        }
        return outbound;
    }

    private OpenAgentSocket openHello(Map<String, Object> hello) throws Exception {
        var response = new CompletableFuture<AgentOutboundMessage>();
        var sessionRef = new CompletableFuture<WebSocketSession>();
        var client = new StandardWebSocketClient();
        var handler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessionRef.complete(session);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(hello)));
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                response.complete(objectMapper.readValue(message.getPayload(), AgentOutboundMessage.class));
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                response.completeExceptionally(exception);
            }
        };

        client.execute(handler, "ws://localhost:" + port + "/agent/ws")
                .get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
        return new OpenAgentSocket(
                response.get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS),
                sessionRef.get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS));
    }

    private TestUser signup() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var body = """
                {
                  "email": "agent-%s@example.com",
                  "password": "password-1234",
                  "name": "Agent Admin"
                }
                """.formatted(suffix);
        var signupResult = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        var accessToken = objectMapper.readTree(signupResult.getResponse().getContentAsString())
                .get("accessToken")
                .asText();

        var meResult = mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();
        var me = objectMapper.readTree(meResult.getResponse().getContentAsString());
        return new TestUser(
                me.get("offices").get(0).get("id").asLong(),
                accessToken);
    }

    private String issueInstallToken(TestUser user) throws Exception {
        var result = mockMvc.perform(post("/api/v1/archdox-agents/install-tokens")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInMinutes\":5}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private List<ArchDoxAgentSession> waitForSessions(Long agentId, int expectedCount) throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            var sessions = sessionRepository.findByAgentIdOrderByConnectedAtAsc(agentId);
            if (sessions.size() >= expectedCount
                    && sessions.stream().allMatch(session -> session.status() == ArchDoxAgentSessionStatus.DISCONNECTED)) {
                return sessions;
            }
            Thread.sleep(100);
        }
        fail("Expected " + expectedCount + " ArchDox Agent sessions for agent " + agentId);
        return List.of();
    }

    private record TestUser(long officeId, String accessToken) {
    }

    private record OpenAgentSocket(AgentOutboundMessage welcome, WebSocketSession session) {
    }
}

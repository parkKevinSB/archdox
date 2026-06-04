package com.archdox.cloud.agent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.archdox.cloud.agent.application.AgentOutboundMessage;
import com.archdox.cloud.agent.application.ArchDoxAgentConnectionHealthService;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommand;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommandStatus;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommandType;
import com.archdox.cloud.agent.domain.ArchDoxAgentSessionStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentCommandRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentSessionRepository;
import com.archdox.cloud.document.domain.DocumentJob;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
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
class ArchDoxAgentDocumentRenderConnectionIntegrationTest {
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("archdox.agent.api-instance-id", () -> "agent-render-connection-test-api");
        registry.add("archdox.agent.connection-health.heartbeat-timeout-ms", () -> "1000");
        registry.add("archdox.ai-review.worker-interval-ms", () -> "10");
        registry.add("archdox.documents.generation.worker-interval-ms", () -> "10");
        registry.add("archdox.documents.generation.retry-base-delay-ms", () -> "10");
        registry.add("archdox.documents.storage.local-root", () -> "build/test-document-storage");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ArchDoxAgentConnectionHealthService connectionHealthService;

    @Autowired
    ArchDoxAgentSessionRepository sessionRepository;

    @Autowired
    ArchDoxAgentCommandRepository commandRepository;

    @Autowired
    DocumentJobRepository documentJobRepository;

    @LocalServerPort
    int port;

    @Test
    void inProgressDocumentGenerationRejectsDuplicateConnectionThenFailsOnHeartbeatTimeoutAndReconnects() throws Exception {
        var user = signup();
        var installToken = issueInstallToken(user);
        var agent = openAgent(Map.of(
                "type", "HELLO",
                "authMode", "INSTALL_TOKEN",
                "officeId", user.officeId(),
                "agentCode", "office-main",
                "installToken", installToken,
                "version", "0.0.1-test",
                "capabilities", Map.of(
                        "documentGeneration", true,
                        "outputFormats", List.of("DOCX"))));

        try {
            assertEquals("WELCOME", agent.welcome().type());
            assertNotNull(agent.welcome().agentId());
            assertNotNull(agent.welcome().deviceSecret());

            var projectId = createProject(user);
            var siteId = createSite(user, projectId);
            var reportId = createReport(user, projectId, siteId);
            saveBasicInfoStep(user, reportId);
            saveDailyLogStep(user, reportId);
            uploadWorkingPhoto(user, projectId, reportId);
            submitReport(user, reportId);
            passPreflight(user, reportId);

            var jobId = createArchDoxAgentDocumentJob(user, reportId);
            var command = agent.nextCommand().get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            assertEquals("COMMAND", command.type());
            assertEquals(ArchDoxAgentCommandType.GENERATE_DOCUMENT, command.commandType());
            assertEquals(jobId, longValue(command.payload().get("documentJobId")));
            waitForCommandStatus(command.commandId(), ArchDoxAgentCommandStatus.ACKED);
            waitForJobStatus(jobId, DocumentJobStatus.GENERATING);

            var duplicate = sendHello(Map.of(
                    "type", "HELLO",
                    "authMode", "DEVICE_SECRET",
                    "agentId", agent.welcome().agentId(),
                    "deviceSecret", agent.welcome().deviceSecret(),
                    "version", "0.0.1-test",
                    "capabilities", Map.of(
                            "documentGeneration", true,
                            "outputFormats", List.of("DOCX"))));
            assertEquals("ERROR", duplicate.type());
            assertEquals("ArchDox Agent is already connected", duplicate.message());
            assertEquals(1, activeSessionCount(agent.welcome().agentId()));

            forceHeartbeatTimeout(agent.welcome().agentId());
            agent.closed().get(WAIT.toMillis(), TimeUnit.MILLISECONDS);

            assertEquals(0, activeSessionCount(agent.welcome().agentId()));
            var failedCommand = waitForCommandStatus(command.commandId(), ArchDoxAgentCommandStatus.FAILED);
            assertEquals("ARCHDOX_AGENT_DISCONNECTED", failedCommand.resultJson().get("errorCode"));
            assertEquals(Boolean.FALSE, failedCommand.resultJson().get("retryable"));

            var failedJob = waitForJobStatus(jobId, DocumentJobStatus.FAILED);
            assertEquals("ARCHDOX_AGENT_DISCONNECTED", failedJob.errorCode());

            var reconnected = openAgent(Map.of(
                    "type", "HELLO",
                    "authMode", "DEVICE_SECRET",
                    "agentId", agent.welcome().agentId(),
                    "deviceSecret", agent.welcome().deviceSecret(),
                    "version", "0.0.1-test",
                    "capabilities", Map.of(
                            "documentGeneration", true,
                            "outputFormats", List.of("DOCX"))));
            try {
                assertEquals("WELCOME", reconnected.welcome().type());
                assertEquals(agent.welcome().agentId(), reconnected.welcome().agentId());
                assertEquals(1, activeSessionCount(agent.welcome().agentId()));
            } finally {
                closeQuietly(reconnected.session());
            }
            waitForNoActiveSessions(agent.welcome().agentId());
        } finally {
            closeQuietly(agent.session());
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
                .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        var outbound = response.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        var session = sessionRef.getNow(null);
        closeQuietly(session);
        return outbound;
    }

    private OpenAgentSocket openAgent(Map<String, Object> hello) throws Exception {
        var welcome = new CompletableFuture<AgentOutboundMessage>();
        var nextCommand = new CompletableFuture<AgentOutboundMessage>();
        var closed = new CompletableFuture<CloseStatus>();
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
                var outbound = objectMapper.readValue(message.getPayload(), AgentOutboundMessage.class);
                if ("WELCOME".equals(outbound.type())) {
                    welcome.complete(outbound);
                    return;
                }
                if ("COMMAND".equals(outbound.type())) {
                    nextCommand.complete(outbound);
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                            "type", "ACK",
                            "commandId", outbound.commandId()))));
                    return;
                }
                welcome.complete(outbound);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closed.complete(status);
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                welcome.completeExceptionally(exception);
                nextCommand.completeExceptionally(exception);
                closed.completeExceptionally(exception);
            }
        };

        client.execute(handler, "ws://localhost:" + port + "/agent/ws")
                .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        return new OpenAgentSocket(
                welcome.get(WAIT.toMillis(), TimeUnit.MILLISECONDS),
                nextCommand,
                closed,
                sessionRef.get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
    }

    private void forceHeartbeatTimeout(Long agentId) {
        var active = sessionRepository.findByAgentIdAndStatus(agentId, ArchDoxAgentSessionStatus.ACTIVE);
        assertEquals(1, active.size());
        var session = active.get(0);
        session.touch(OffsetDateTime.now().minusMinutes(5));
        sessionRepository.saveAndFlush(session);
        assertEquals(1, connectionHealthService.disconnectHeartbeatTimedOutSessions(agentId));
    }

    private long activeSessionCount(Long agentId) {
        return sessionRepository.findByAgentIdAndStatus(agentId, ArchDoxAgentSessionStatus.ACTIVE).size();
    }

    private void waitForNoActiveSessions(Long agentId) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (activeSessionCount(agentId) == 0) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Expected no active ArchDox Agent session for agent " + agentId);
    }

    private ArchDoxAgentCommand waitForCommandStatus(Long commandId, ArchDoxAgentCommandStatus status) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            var command = commandRepository.findById(commandId);
            if (command.isPresent() && command.get().status() == status) {
                return command.get();
            }
            Thread.sleep(50);
        }
        fail("Expected command " + commandId + " to reach " + status);
        return null;
    }

    private DocumentJob waitForJobStatus(Long jobId, DocumentJobStatus status) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            var job = documentJobRepository.findById(jobId).orElseThrow();
            if (job.status() == status) {
                return job;
            }
            Thread.sleep(50);
        }
        fail("Expected document job " + jobId + " to reach " + status);
        return null;
    }

    private TestUser signup() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var signupResult = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "agent-render-%s@example.com",
                                  "password": "password-1234",
                                  "name": "Agent Render User"
                                }
                                """.formatted(suffix)))
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
        return new TestUser(me.get("offices").get(0).get("id").asLong(), accessToken);
    }

    private String issueInstallToken(TestUser user) throws Exception {
        var result = mockMvc.perform(post("/api/v1/archdox-agents/install-tokens")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInMinutes\":5}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createProject(TestUser user) throws Exception {
        var result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Agent Render Tower\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long createSite(TestUser user, long projectId) throws Exception {
        var result = mockMvc.perform(post("/api/v1/projects/{projectId}/sites", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "siteCode": "SITE-AGENT",
                                  "name": "Agent Site",
                                  "address": "Seoul",
                                  "siteType": "BUILDING"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long createReport(TestUser user, long projectId, long siteId) throws Exception {
        var result = mockMvc.perform(post("/api/v1/inspection-reports")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "siteId": %d,
                                  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                                  "title": "Agent render report"
                                }
                                """.formatted(projectId, siteId)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private void saveBasicInfoStep(TestUser user, long reportId) throws Exception {
        mockMvc.perform(put("/api/v1/inspection-reports/{reportId}/steps/{stepCode}", reportId, "BASIC_INFO")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payload": {
                                    "inspectionDate": "2026-05-23",
                                    "inspectorName": "Agent Render",
                                    "chiefSupervisorName": "Agent Render",
                                    "weather": "Clear",
                                    "location": "Site A"
                                  }
                                }
                                """))
                .andExpect(status().isOk());
    }

    private void saveDailyLogStep(TestUser user, long reportId) throws Exception {
        mockMvc.perform(put("/api/v1/inspection-reports/{reportId}/steps/{stepCode}", reportId, "DAILY_LOG")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payload": {
                                    "dailyItems": {
                                      "groups": [
                                        {
                                          "tradeCode": "REINFORCED_CONCRETE",
                                          "tradeName": "Reinforced concrete",
                                          "processCode": "REBAR_ASSEMBLY",
                                          "processName": "Rebar assembly",
                                          "floor": "1F",
                                          "entries": [
                                            {
                                              "inspectionItemCode": "RC_REBAR_COUNT_DIAMETER_PITCH",
                                              "inspectionItemName": "Rebar count and pitch",
                                              "supervisionContent": "Checked rebar spacing",
                                              "photoIds": []
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk());
    }

    private void uploadWorkingPhoto(TestUser user, long projectId, long reportId) throws Exception {
        var bytes = "agent-render-working-photo".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var hash = sha256(bytes);
        var intentResult = mockMvc.perform(post("/api/v1/photos/intent")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "reportId": %d,
                                  "stepCode": "PHOTOS",
                                  "captureKind": "UPLOAD",
                                  "mime": "image/png",
                                  "bytes": %d,
                                  "hash": "%s",
                                  "width": 640,
                                  "height": 480,
                                  "wantsOriginal": false
                                }
                                """.formatted(projectId, reportId, bytes.length, hash)))
                .andExpect(status().isCreated())
                .andReturn();
        var photoId = objectMapper.readTree(intentResult.getResponse().getContentAsString()).get("photoId").asLong();
        mockMvc.perform(put("/api/v1/photos/{photoId}/content/{kind}", photoId, "WORKING")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.IMAGE_PNG)
                        .content(bytes))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/photos/{photoId}/confirm", photoId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hash": "%s",
                                  "bytes": %d,
                                  "width": 640,
                                  "height": 480
                                }
                                """.formatted(hash, bytes.length)))
                .andExpect(status().isOk());
    }

    private void submitReport(TestUser user, long reportId) throws Exception {
        mockMvc.perform(post("/api/v1/inspection-reports/{reportId}/submit", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_TO_GENERATE"));
    }

    private void passPreflight(TestUser user, long reportId) throws Exception {
        mockMvc.perform(post("/api/v1/inspection-reports/{reportId}/preflight-review-runs", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isCreated());
        for (int i = 0; i < 80; i++) {
            var result = mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/preflight-review-runs", reportId)
                            .header("Authorization", bearer(user.accessToken()))
                            .header("X-Office-Id", user.officeId()))
                    .andExpect(status().isOk())
                    .andReturn();
            var runs = objectMapper.readTree(result.getResponse().getContentAsString());
            if (runs.size() > 0 && "PASSED".equals(runs.get(0).get("status").asText())) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Preflight review did not reach PASSED status");
    }

    private long createArchDoxAgentDocumentJob(TestUser user, long reportId) throws Exception {
        var result = mockMvc.perform(post("/api/v1/inspection-reports/{reportId}/document-jobs", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerType": "ARCHDOX_AGENT",
                                  "outputFormat": "DOCX"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.workerType").value("ARCHDOX_AGENT"))
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private long readId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return Long.parseLong(text);
        }
        throw new IllegalArgumentException("Expected numeric value: " + value);
    }

    private void closeQuietly(WebSocketSession session) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(CloseStatus.NORMAL);
        } catch (Exception ignored) {
            // Test cleanup only.
        }
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(long officeId, String accessToken) {
    }

    private record OpenAgentSocket(
            AgentOutboundMessage welcome,
            CompletableFuture<AgentOutboundMessage> nextCommand,
            CompletableFuture<CloseStatus> closed,
            WebSocketSession session
    ) {
    }
}

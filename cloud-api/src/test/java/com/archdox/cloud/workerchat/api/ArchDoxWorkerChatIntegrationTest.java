package com.archdox.cloud.workerchat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.archdox.cloud.aipolicy.domain.AiCredentialDeliveryMode;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import com.archdox.cloud.aipolicy.infra.OfficeAiPolicyRepository;
import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentDeploymentMode;
import com.archdox.cloud.agent.infra.ArchDoxAgentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ArchDoxWorkerChatIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("archdox.ai.fake-provider.enabled", () -> "true");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AiProviderCredentialRepository providerRepository;

    @Autowired
    OfficeAiPolicyRepository officeAiPolicyRepository;

    @Autowired
    ArchDoxAgentRepository agentRepository;

    @Test
    void workerChatCreatesSiteReportAndUpdatesReportStepThroughWorkerActions() throws Exception {
        simplifyDailyLogWorkflowForSubmitTest();
        var user = signup("worker-chat@example.com", "Worker Chat");
        var projectId = createProject(user.accessToken(), user.officeId(), "Worker Chat Project");

        mockMvc.perform(get("/api/v1/projects/{projectId}/worker-chat", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.stage").value("AWAITING_SITE"))
                .andExpect(jsonPath("$.messages[0].metadata.nextAction").value("CREATE_SITE"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/worker-chat/messages", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "현장 생성: 1공구 현장",
                                  "createSite": {
                                    "name": "1공구 현장",
                                    "address": "서울시 테스트로 1",
                                    "siteType": "CONSTRUCTION_SITE"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        var afterSite = awaitLastAssistantReply(user, projectId);
        var createdSiteId = afterSite.get("siteId").asLong();
        var siteReply = lastMessage(afterSite);
        assertThat(afterSite.get("stage").asText()).isEqualTo("AWAITING_REPORT");
        assertThat(createdSiteId).isPositive();
        assertThat(siteReply.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(siteReply.get("metadata").get("createdSiteId").asLong()).isEqualTo(createdSiteId);
        assertThat(siteReply.get("metadata").get("nextAction").asText()).isEqualTo("CREATE_REPORT");

        mockMvc.perform(post("/api/v1/projects/{projectId}/worker-chat/messages", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "리포트 생성: 오늘 감리일지",
                                  "createReport": {
                                    "title": "오늘 감리일지",
                                    "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        var afterReport = awaitLastAssistantReply(user, projectId);
        var createdReportId = afterReport.get("reportId").asLong();
        var reportReply = lastMessage(afterReport);
        assertThat(afterReport.get("stage").asText()).isEqualTo("REPORT_WORKING");
        assertThat(createdReportId).isPositive();
        assertThat(reportReply.get("metadata").get("createdReportId").asLong()).isEqualTo(createdReportId);
        assertThat(reportReply.get("metadata").get("nextAction").asText()).isEqualTo("UPDATE_REPORT_STEP");

        mockMvc.perform(post("/api/v1/projects/{projectId}/worker-chat/messages", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "철근 배근 상태 확인 완료",
                                  "updateReportStep": {
                                    "stepCode": "BASIC_INFO",
                                    "payload": {
                                      "inspectionDate": "2026-06-01",
                                      "chiefSupervisorName": "Worker Supervisor",
                                      "workerNote": "Worker chat saved basic info",
                                      "source": "WORKER_CHAT"
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        var afterStep = awaitLastAssistantReply(user, projectId);
        var stepReply = lastMessage(afterStep);
        assertThat(stepReply.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(stepReply.get("metadata").get("savedStepCode").asText()).isEqualTo("BASIC_INFO");

        var stepsResult = mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/steps", createdReportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andReturn();
        var steps = objectMapper.readTree(stepsResult.getResponse().getContentAsString());
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).get("stepCode").asText()).isEqualTo("BASIC_INFO");
        assertThat(steps.get(0).get("payload").get("workerNote").asText()).isEqualTo("Worker chat saved basic info");

        mockMvc.perform(post("/api/v1/projects/{projectId}/worker-chat/messages", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "submit report",
                                  "submitReport": {
                                    "reportId": %d
                                  }
                                }
                                """.formatted(createdReportId)))
                .andExpect(status().isOk());

        var afterSubmit = awaitLastAssistantReply(user, projectId);
        var submitReply = lastMessage(afterSubmit);
        assertThat(afterSubmit.get("stage").asText()).isEqualTo("REVIEWING");
        assertThat(afterSubmit.get("reportId").asLong()).isEqualTo(createdReportId);
        assertThat(submitReply.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(submitReply.get("metadata").get("actionType").asText()).isEqualTo("SUBMIT_REPORT");
        assertThat(submitReply.get("metadata").get("documentTabAvailable").asBoolean()).isTrue();
        assertThat(submitReply.get("metadata").get("reportStatus").asText()).isEqualTo("READY_TO_GENERATE");
        assertThat(submitReply.get("metadata").get("nextAction").asText()).isEqualTo("RUN_PREFLIGHT_REVIEW");

        var reportResult = mockMvc.perform(get("/api/v1/inspection-reports/{reportId}", createdReportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andReturn();
        var report = objectMapper.readTree(reportResult.getResponse().getContentAsString());
        assertThat(report.get("status").asText()).isEqualTo("READY_TO_GENERATE");

        mockMvc.perform(post("/api/v1/projects/{projectId}/worker-chat/messages", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "run preflight review",
                                  "runPreflightReview": {
                                    "reportId": %d
                                  }
                                }
                                """.formatted(createdReportId)))
                .andExpect(status().isOk());

        var afterPreflight = awaitLastAssistantReply(user, projectId);
        var preflightReply = lastMessage(afterPreflight);
        var preflightRunId = preflightReply.get("metadata").get("preflightRunId").asLong();
        assertThat(afterPreflight.get("stage").asText()).isEqualTo("REVIEWING");
        assertThat(preflightRunId).isPositive();
        assertThat(preflightReply.get("metadata").get("actionType").asText()).isEqualTo("RUN_PREFLIGHT_REVIEW");
        assertThat(preflightReply.get("metadata").get("nextAction").asText()).isEqualTo("REQUEST_DOCUMENT_GENERATION");
        waitForPreflightStatus(user, createdReportId, preflightRunId, "PASSED");
        var syncedAfterPreflight = openWorkerChat(user, projectId);
        assertThat(syncedAfterPreflight.get("workflowState").get("latestPreflightRun").get("status").asText()).isEqualTo("PASSED");
        assertThat(syncedAfterPreflight.get("workflowState").get("canRequestDocumentGeneration").asBoolean()).isTrue();

        registerDocumentAgent(user.officeId());
        mockMvc.perform(post("/api/v1/projects/{projectId}/worker-chat/messages", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "request document generation",
                                  "requestDocumentGeneration": {
                                    "reportId": %d,
                                    "outputFormat": "DOCX"
                                  }
                                }
                                """.formatted(createdReportId)))
                .andExpect(status().isOk());

        var afterGeneration = awaitLastAssistantReply(user, projectId);
        var generationReply = lastMessage(afterGeneration);
        var documentJobId = generationReply.get("metadata").get("documentJobId").asLong();
        assertThat(afterGeneration.get("stage").asText()).isEqualTo("GENERATING_DOCUMENT");
        assertThat(documentJobId).isPositive();
        assertThat(generationReply.get("metadata").get("actionType").asText()).isEqualTo("REQUEST_DOCUMENT_GENERATION");

        var jobResult = mockMvc.perform(get("/api/v1/document-jobs/{jobId}", documentJobId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andReturn();
        var job = objectMapper.readTree(jobResult.getResponse().getContentAsString());
        assertThat(job.get("reportId").asLong()).isEqualTo(createdReportId);
        assertThat(job.get("workerType").asText()).isEqualTo("ARCHDOX_AGENT");
        var syncedAfterGeneration = openWorkerChat(user, projectId);
        assertThat(syncedAfterGeneration.get("workflowState").get("latestDocumentJob").get("id").asLong()).isEqualTo(documentJobId);
    }

    @Test
    void workerChatPlainTextCanProducePlannerProposalWithoutMutatingDomainState() throws Exception {
        var user = signup("worker-chat-planner@example.com", "Worker Planner");
        var projectId = createProject(user.accessToken(), user.officeId(), "Worker Planner Project");
        enableFakeWorkerPlannerAi(user.officeId());

        mockMvc.perform(get("/api/v1/projects/{projectId}/worker-chat", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("AWAITING_SITE"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/worker-chat/messages", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "인우빌딩 현장 만들어줘"
                                }
                                """))
                .andExpect(status().isOk());

        var afterPlanner = awaitLastAssistantReply(user, projectId);
        var reply = lastMessage(afterPlanner);
        var proposal = reply.get("metadata").get("plannerProposal");
        assertThat(proposal).isNotNull();
        assertThat(proposal.get("decision").asText()).isEqualTo("PROPOSE_ACTION");
        assertThat(proposal.get("actionType").asText()).isEqualTo("CREATE_SITE");
        assertThat(reply.get("metadata").get("nextAction").asText()).isEqualTo("CREATE_SITE");
        assertThat(afterPlanner.get("stage").asText()).isEqualTo("AWAITING_SITE");
        assertThat(afterPlanner.get("siteId").isNull()).isTrue();
    }

    @Test
    void workerChatCanCancelPendingAssistantAction() throws Exception {
        var user = signup("worker-chat-cancel@example.com", "Worker Cancel");
        var projectId = createProject(user.accessToken(), user.officeId(), "Worker Cancel Project");
        var opened = openWorkerChat(user, projectId);
        var sessionId = opened.get("id").asLong();
        var pendingId = jdbcTemplate.queryForObject("""
                insert into archdox_worker_chat_messages (
                    office_id,
                    session_id,
                    user_id,
                    role,
                    status,
                    content,
                    worker_request_id,
                    worker_action_type,
                    metadata_json,
                    created_at,
                    updated_at
                )
                values (?, ?, null, 'ASSISTANT', 'PENDING', '작업 중입니다.', ?, 'WORKER_CHAT_ADVANCE', '{}'::jsonb, now(), now())
                returning id
                """,
                Long.class,
                user.officeId(),
                sessionId,
                UUID.randomUUID());

        var cancelledResult = mockMvc.perform(post("/api/v1/projects/{projectId}/worker-chat/cancel", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andReturn();

        var cancelled = objectMapper.readTree(cancelledResult.getResponse().getContentAsString());
        var last = lastMessage(cancelled);
        assertThat(last.get("id").asLong()).isEqualTo(pendingId);
        assertThat(last.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(last.get("metadata").get("cancelled").asBoolean()).isTrue();
    }

    private JsonNode awaitLastAssistantReply(TestUser user, long projectId) throws Exception {
        JsonNode latest = null;
        for (int i = 0; i < 20; i++) {
            var result = mockMvc.perform(get("/api/v1/projects/{projectId}/worker-chat", projectId)
                            .header("Authorization", bearer(user.accessToken()))
                            .header("X-Office-Id", user.officeId()))
                    .andExpect(status().isOk())
                    .andReturn();
            latest = objectMapper.readTree(result.getResponse().getContentAsString());
            var last = lastMessage(latest);
            if ("ASSISTANT".equals(last.get("role").asText())
                    && "COMPLETED".equals(last.get("status").asText())) {
                return latest;
            }
            Thread.sleep(150);
        }
        return latest;
    }

    private JsonNode openWorkerChat(TestUser user, long projectId) throws Exception {
        var result = mockMvc.perform(get("/api/v1/projects/{projectId}/worker-chat", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode lastMessage(JsonNode session) {
        var messages = session.get("messages");
        return messages.get(messages.size() - 1);
    }

    private TestUser signup(String email, String name) throws Exception {
        var body = """
                {
                  "email": "%s",
                  "password": "password-1234",
                  "name": "%s"
                }
                """.formatted(email, name);
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
        return new TestUser(me.get("offices").get(0).get("id").asLong(), accessToken);
    }

    private long createProject(String accessToken, long officeId, String name) throws Exception {
        var result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(accessToken))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void simplifyDailyLogWorkflowForSubmitTest() {
        jdbcTemplate.update("""
                update document_type_definitions
                set workflow_json = cast(? as jsonb)
                where office_id is null
                  and code = ?
                """,
                """
                {
                  "flowId": "worker-chat-submit-smoke",
                  "title": "Worker Chat Submit Smoke",
                  "steps": [
                    {
                      "code": "BASIC_INFO",
                      "title": "Basic Info",
                      "description": "Worker chat submit smoke step.",
                      "stepType": "FORM",
                      "fields": [
                        {"key": "inspectionDate", "label": "Inspection date", "type": "date", "required": true},
                        {"key": "chiefSupervisorName", "label": "Chief supervisor", "type": "text", "required": true}
                      ]
                    }
                  ]
                }
                """,
                "CONSTRUCTION_DAILY_SUPERVISION_LOG");
    }

    private void waitForPreflightStatus(TestUser user, long reportId, long runId, String expectedStatus) throws Exception {
        for (int i = 0; i < 80; i++) {
            var result = mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/preflight-review-runs", reportId)
                            .header("Authorization", bearer(user.accessToken()))
                            .header("X-Office-Id", user.officeId()))
                    .andExpect(status().isOk())
                    .andReturn();
            var runs = objectMapper.readTree(result.getResponse().getContentAsString());
            for (var run : runs) {
                if (run.get("id").asLong() == runId && expectedStatus.equals(run.get("status").asText())) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Preflight review " + runId + " did not reach " + expectedStatus);
    }

    private void registerDocumentAgent(long officeId) {
        agentRepository.saveAndFlush(new ArchDoxAgent(
                officeId,
                "worker-chat-docgen-agent",
                ArchDoxAgentDeploymentMode.CLOUD_MANAGED,
                "test",
                Map.of(
                        "documentRender", true,
                        "outputFormats", List.of("DOCX")),
                Map.of(),
                OffsetDateTime.now()));
    }

    private void enableFakeWorkerPlannerAi(long officeId) {
        var now = OffsetDateTime.now();
        var provider = providerRepository.saveAndFlush(new AiProviderCredential(
                "fake-worker-" + officeId,
                "Fake Worker AI",
                AiProviderType.CUSTOM_HTTP,
                null,
                "fake-worker-model",
                null,
                null,
                null,
                now));
        provider.publish(now);
        providerRepository.saveAndFlush(provider);
        var policy = officeAiPolicyRepository.findByOfficeId(officeId)
                .orElseGet(() -> officeAiPolicyRepository.saveAndFlush(new OfficeAiPolicy(officeId, null, now)));
        policy.update(
                true,
                false,
                true,
                provider.id(),
                AiCredentialDeliveryMode.PROXY_ONLY,
                false,
                null,
                "USD",
                null,
                null,
                null,
                now);
        officeAiPolicyRepository.saveAndFlush(policy);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(long officeId, String accessToken) {
    }
}

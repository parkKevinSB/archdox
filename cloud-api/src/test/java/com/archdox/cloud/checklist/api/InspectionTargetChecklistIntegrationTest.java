package com.archdox.cloud.checklist.api;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentDeploymentMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentSession;
import com.archdox.cloud.agent.infra.ArchDoxAgentRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentSessionRepository;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class InspectionTargetChecklistIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("archdox.ai-review.worker-interval-ms", () -> "10");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DocumentJobRepository documentJobRepository;

    @Autowired
    ArchDoxAgentRepository agentRepository;

    @Autowired
    ArchDoxAgentSessionRepository agentSessionRepository;

    @Test
    void createsTargetSavesChecklistAnswerAndSnapshotsDocumentInput() throws Exception {
        var user = signup("target-checklist@example.com", "Target Checklist");
        registerDocumentAgent(user.officeId());
        var projectId = createProject(user);
        var siteId = createSite(user, projectId);
        var targetId = createTarget(user, projectId, siteId);
        var reportId = createReport(user, projectId, siteId);

        mockMvc.perform(get("/api/v1/projects/{projectId}/sites/{siteId}/targets", projectId, siteId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Main Building"))
                .andExpect(jsonPath("$[0].targetType").value("BUILDING"));

        mockMvc.perform(post("/api/v1/inspection-reports/{reportId}/targets", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetId": %d,
                                  "role": "PRIMARY"
                                }
                                """.formatted(targetId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetId").value(targetId))
                .andExpect(jsonPath("$.role").value("PRIMARY"))
                .andExpect(jsonPath("$.snapshot.name").value("Main Building"));

        var checklistResult = mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/checklist", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema.code").value("CONSTRUCTION_SUPERVISION_REPORT_DEFAULT"))
                .andExpect(jsonPath("$.schema.items", hasSize(4)))
                .andExpect(jsonPath("$.answers", hasSize(0)))
                .andReturn();
        var fieldInvestigationItemId = checklistItemId(
                checklistResult.getResponse().getContentAsString(),
                "FIELD_INVESTIGATION");

        mockMvc.perform(put("/api/v1/inspection-reports/{reportId}/checklist/answers/{itemCode}", reportId, "FIELD_INVESTIGATION")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetId": %d,
                                  "answer": {
                                    "value": "관찰"
                                  },
                                  "note": "2층 복도 미세 균열 관찰"
                                }
                                """.formatted(targetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCode").value("FIELD_INVESTIGATION"))
                .andExpect(jsonPath("$.answer.value").value("관찰"))
                .andExpect(jsonPath("$.note").value("2층 복도 미세 균열 관찰"));

        saveBasicInfoStep(user, reportId);
        uploadWorkingPhoto(user, projectId, reportId, fieldInvestigationItemId);

        mockMvc.perform(post("/api/v1/inspection-reports/{reportId}/submit", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_TO_GENERATE"));
        passPreflight(user, reportId);

        var jobResult = mockMvc.perform(post("/api/v1/inspection-reports/{reportId}/document-jobs", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        var jobId = objectMapper.readTree(jobResult.getResponse().getContentAsString()).get("id").asLong();
        var snapshot = documentJobRepository.findById(jobId).orElseThrow().inputSnapshotJson();
        var targets = (List<?>) snapshot.get("targets");
        var checklistAnswers = (List<?>) snapshot.get("checklistAnswers");
        assertFalse(targets.isEmpty());
        assertFalse(checklistAnswers.isEmpty());
        var firstAnswer = (Map<?, ?>) checklistAnswers.get(0);
        assertEquals("FIELD_INVESTIGATION", firstAnswer.get("itemCode"));
        assertEquals(1, firstAnswer.get("photoCount"));
        var answerPhotos = (List<?>) firstAnswer.get("photos");
        assertFalse(answerPhotos.isEmpty());
        var firstPhoto = (Map<?, ?>) answerPhotos.get(0);
        assertEquals("FIELD_INVESTIGATION", firstPhoto.get("checklistItemCode"));
        assertEquals(fieldInvestigationItemId, ((Number) firstPhoto.get("checklistItemId")).longValue());
        var checklistPhotos = (List<?>) snapshot.get("checklistPhotos");
        assertFalse(checklistPhotos.isEmpty());
        var firstChecklistPhotoGroup = (Map<?, ?>) checklistPhotos.get(0);
        assertEquals("FIELD_INVESTIGATION", firstChecklistPhotoGroup.get("itemCode"));
        assertEquals(1, firstChecklistPhotoGroup.get("photoCount"));
    }

    private TestUser signup(String email, String name) throws Exception {
        var signupResult = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password-1234",
                                  "name": "%s"
                                }
                                """.formatted(email, name)))
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

    private long createProject(TestUser user) throws Exception {
        var result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Construction Supervision Project",
                                  "buildingType": "CONSTRUCTION_SUPERVISION"
                                }
                                """))
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
                                  "siteCode": "SITE-1",
                                  "name": "Safety Site",
                                  "siteType": "BUILDING"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long createTarget(TestUser user, long projectId, long siteId) throws Exception {
        var result = mockMvc.perform(post("/api/v1/projects/{projectId}/sites/{siteId}/targets", projectId, siteId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "BUILDING",
                                  "code": "B-01",
                                  "name": "Main Building",
                                  "address": "North block"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType").value("BUILDING"))
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
                                  "reportType": "CONSTRUCTION_SUPERVISION_REPORT",
                                  "title": "Safety report"
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
                                    "inspectorName": "Target Checklist",
                                    "supervisorName": "Target Checklist",
                                    "weather": "Clear",
                                    "location": "Safety Site"
                                  }
                                }
                                """))
                .andExpect(status().isOk());
    }

    private void uploadWorkingPhoto(TestUser user, long projectId, long reportId, long checklistItemId) throws Exception {
        var bytes = "checklist-working-photo".getBytes(StandardCharsets.UTF_8);
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
                                  "checklistItemId": %d,
                                  "captureKind": "UPLOAD",
                                  "mime": "image/png",
                                  "bytes": %d,
                                  "hash": "%s",
                                  "width": 640,
                                  "height": 480,
                                  "wantsOriginal": false
                                }
                                """.formatted(projectId, reportId, checklistItemId, bytes.length, hash)))
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

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private long readId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }

    private long checklistItemId(String json, String itemCode) throws Exception {
        for (JsonNode item : objectMapper.readTree(json).get("schema").get("items")) {
            if (itemCode.equals(item.get("itemCode").asText())) {
                return item.get("id").asLong();
            }
        }
        throw new AssertionError("Checklist item not found: " + itemCode);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void registerDocumentAgent(long officeId) {
        var now = java.time.OffsetDateTime.now();
        var agent = agentRepository.save(new ArchDoxAgent(
                officeId,
                "docgen-agent-" + officeId,
                ArchDoxAgentDeploymentMode.LOCAL_OFFICE,
                "test",
                Map.of(
                        "documentGeneration", true,
                        "outputFormats", List.of("DOCX")),
                Map.of("artifact", Map.of("kind", "LOCAL_FS")),
                now));
        agentSessionRepository.save(new ArchDoxAgentSession(
                agent,
                "integration-test-api",
                "integration-test-ws-" + officeId,
                now));
    }

    private record TestUser(long officeId, String accessToken) {
    }
}

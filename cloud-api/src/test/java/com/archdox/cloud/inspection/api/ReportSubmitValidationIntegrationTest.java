package com.archdox.cloud.inspection.api;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
class ReportSubmitValidationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void blocksSubmitUntilRequiredReportInputsExist() throws Exception {
        var user = signup();
        var projectId = createProject(user);
        var reportId = createReport(user, projectId);

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/submit-validation", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.blockingIssues", hasSize(3)))
                .andExpect(jsonPath("$.blockingIssues[*].code", hasItems(
                        "MISSING_STEP_BASIC_INFO",
                        "MISSING_STEP_DAILY_LOG",
                        "MISSING_WORKING_PHOTO")));

        mockMvc.perform(post("/api/v1/inspection-reports/{reportId}/submit", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.blockingIssues[*].code", hasItems(
                        "MISSING_STEP_BASIC_INFO",
                        "MISSING_STEP_DAILY_LOG",
                        "MISSING_WORKING_PHOTO")));

        saveBasicInfoStep(user, reportId);
        saveDailyLogStep(user, reportId);
        uploadWorkingPhoto(user, projectId, reportId);

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/submit-validation", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.blockingIssues", hasSize(0)));

        mockMvc.perform(post("/api/v1/inspection-reports/{reportId}/submit", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_TO_GENERATE"));
    }

    @Test
    void reopensSubmittedReportAsNextEditableRevision() throws Exception {
        var user = signup("submit-reopen@example.com", "Submit Reopen");
        var projectId = createProject(user);
        var reportId = createReport(user, projectId);
        saveBasicInfoStep(user, reportId);
        saveDailyLogStep(user, reportId);
        uploadWorkingPhoto(user, projectId, reportId);

        mockMvc.perform(post("/api/v1/inspection-reports/{reportId}/submit", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_TO_GENERATE"))
                .andExpect(jsonPath("$.contentRevision").value(1))
                .andExpect(jsonPath("$.submittedRevision").value(1));

        mockMvc.perform(put("/api/v1/inspection-reports/{reportId}/steps/{stepCode}", reportId, "BASIC_INFO")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "payload": {
                            "weather": "Cloudy"
                          }
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_STEP_SAVE_NOT_ALLOWED"))
                .andExpect(jsonPath("$.messageKey").value("errors.report.stepSaveNotAllowed"));

        mockMvc.perform(post("/api/v1/inspection-reports/{reportId}/reopen", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STEP_SAVED"))
                .andExpect(jsonPath("$.contentRevision").value(2))
                .andExpect(jsonPath("$.submittedRevision").value(1));

        saveBasicInfoStep(user, reportId);

        mockMvc.perform(post("/api/v1/inspection-reports/{reportId}/submit", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_TO_GENERATE"))
                .andExpect(jsonPath("$.contentRevision").value(2))
                .andExpect(jsonPath("$.submittedRevision").value(2));
    }

    @Test
    void resolvesWorkflowDefinitionForReportWritingUi() throws Exception {
        var user = signup("workflow-definition@example.com", "Workflow Definition");
        var projectId = createProject(user);
        var reportId = createReport(user, projectId);

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/workflow-definition", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DOCUMENT_TYPE_DEFAULT"))
                .andExpect(jsonPath("$.checklistSchemaCode").value("CONSTRUCTION_DAILY_SUPERVISION_LOG_DEFAULT"))
                .andExpect(jsonPath("$.steps[*].code", hasItems("BASIC_INFO", "DAILY_LOG", "PHOTOS", "REMARKS")));

        var definitionId = createWorkflowDefinition(user);
        var revisionId = createWorkflowRevision(user, definitionId);
        publishWorkflowRevision(user, revisionId);
        assignWorkflowOverride(user, revisionId);

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/workflow-definition", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("OFFICE_OVERRIDE"))
                .andExpect(jsonPath("$.revisionId").value(revisionId))
                .andExpect(jsonPath("$.flowId").value("custom-daily-flow"))
                .andExpect(jsonPath("$.steps", hasSize(1)))
                .andExpect(jsonPath("$.steps[0].code").value("CUSTOM_INFO"))
                .andExpect(jsonPath("$.steps[0].fields[0].key").value("memo"));

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/submit-validation", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.blockingIssues[*].code", hasItems("MISSING_STEP_CUSTOM_INFO")));

        mockMvc.perform(put("/api/v1/inspection-reports/{reportId}/steps/{stepCode}", reportId, "CUSTOM_INFO")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payload": {
                                    "memo": "Configured workflow input"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/submit-validation", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.blockingIssues", hasSize(0)));
    }

    @Test
    void appliesRuleSetMinimumWorkingPhotoCount() throws Exception {
        var user = signup("rule-set-validation@example.com", "Rule Set Validation");
        var projectId = createProject(user);
        var reportId = createReport(user, projectId);
        saveBasicInfoStep(user, reportId);
        saveDailyLogStep(user, reportId);
        uploadWorkingPhoto(user, projectId, reportId, "photo-one");

        var ruleSetId = createRuleSet(user);
        var revisionId = createRuleSetRevision(user, ruleSetId);
        publishRuleSetRevision(user, revisionId);
        assignRuleSetOverride(user, revisionId);

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/submit-validation", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.blockingIssues[*].code", hasItems("MISSING_WORKING_PHOTO")));

        uploadWorkingPhoto(user, projectId, reportId, "photo-two");

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/submit-validation", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.blockingIssues", hasSize(0)));
    }

    private TestUser signup() throws Exception {
        return signup("submit-validation@example.com", "Submit Validation");
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
                        .content("{\"name\":\"Validation Project\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long createReport(TestUser user, long projectId) throws Exception {
        var siteId = createSite(user, projectId);
        var result = mockMvc.perform(post("/api/v1/inspection-reports")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "siteId": %d,
                                  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                                  "title": "Validation report"
                                }
                                """.formatted(projectId, siteId)))
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
                                  "siteCode": "SITE-VALIDATION",
                                  "name": "Validation Site",
                                  "address": "Seoul",
                                  "siteType": "BUILDING"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long createWorkflowDefinition(TestUser user) throws Exception {
        var result = mockMvc.perform(post("/api/v1/config/workflow-definitions")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "custom-daily-flow",
                                  "name": "Custom Daily Flow",
                                  "reportType": "construction_daily_supervision_log"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long createWorkflowRevision(TestUser user, long definitionId) throws Exception {
        var result = mockMvc.perform(post("/api/v1/config/workflow-definitions/{definitionId}/revisions", definitionId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payload": {
                                    "flowId": "custom-daily-flow",
                                    "title": "Custom Daily Flow",
                                    "steps": [
                                      {
                                        "code": "custom_info",
                                        "title": "Custom Info",
                                        "description": "Office-specific report step",
                                        "stepType": "form",
                                        "fields": [
                                          {
                                            "key": "memo",
                                            "label": "Memo",
                                            "type": "textarea",
                                            "required": true
                                          }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private void publishWorkflowRevision(TestUser user, long revisionId) throws Exception {
        mockMvc.perform(post("/api/v1/config/workflow-definition-revisions/{revisionId}/publish", revisionId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk());
    }

    private void assignWorkflowOverride(TestUser user, long revisionId) throws Exception {
        mockMvc.perform(put("/api/v1/config/office-overrides/{reportType}", "construction_daily_supervision_log")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workflowRevisionId": %d
                                }
                                """.formatted(revisionId)))
                .andExpect(status().isOk());
    }

    private long createRuleSet(TestUser user) throws Exception {
        var result = mockMvc.perform(post("/api/v1/config/rule-sets")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "daily-photo-rules",
                                  "name": "Daily Photo Rules",
                                  "reportType": "construction_daily_supervision_log"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long createRuleSetRevision(TestUser user, long ruleSetId) throws Exception {
        var result = mockMvc.perform(post("/api/v1/config/rule-sets/{ruleSetId}/revisions", ruleSetId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payload": {
                                    "minWorkingPhotos": 2
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private void publishRuleSetRevision(TestUser user, long revisionId) throws Exception {
        mockMvc.perform(post("/api/v1/config/rule-set-revisions/{revisionId}/publish", revisionId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk());
    }

    private void assignRuleSetOverride(TestUser user, long revisionId) throws Exception {
        mockMvc.perform(put("/api/v1/config/office-overrides/{reportType}", "construction_daily_supervision_log")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ruleSetRevisionId": %d
                                }
                                """.formatted(revisionId)))
                .andExpect(status().isOk());
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
                                    "chiefSupervisorName": "Chief Supervisor",
                                    "inspectorName": "Submit Validation",
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
                                              "inspectionItemCode": "RC_REBAR_CONFIRMATION",
                                              "inspectionItemName": "Rebar confirmation",
                                              "checklistRows": [
                                                {
                                                  "code": "RC_REBAR_COUNT_DIAMETER_PITCH",
                                                  "label": "Checked rebar spacing",
                                                  "result": "COMPLIANT",
                                                  "referenceNote": "",
                                                  "actionNote": "",
                                                  "photoIds": []
                                                }
                                              ],
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
        uploadWorkingPhoto(user, projectId, reportId, "submit-validation-working-photo");
    }

    private void uploadWorkingPhoto(TestUser user, long projectId, long reportId, String content) throws Exception {
        var bytes = content.getBytes(StandardCharsets.UTF_8);
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

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private long readId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(long officeId, String accessToken) {
    }
}

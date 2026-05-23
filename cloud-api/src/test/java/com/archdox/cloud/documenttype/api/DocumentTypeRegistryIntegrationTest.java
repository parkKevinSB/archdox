package com.archdox.cloud.documenttype.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.StreamSupport;
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
class DocumentTypeRegistryIntegrationTest {
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
    void defaultDocumentTypesDriveReportWorkflowAndChecklist() throws Exception {
        var user = signup("document-type@example.com", "Document Type");
        var projectId = createProject(user);

        var documentTypes = mockMvc.perform(get("/api/v1/document-types")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andReturn();
        var documentTypeList = objectMapper.readTree(documentTypes.getResponse().getContentAsString());
        assertTrue(hasCode(documentTypeList, "DEMOLITION_SAFETY_CHECKLIST"));
        assertTrue(hasCode(documentTypeList, "CONSTRUCTION_SUPERVISION_REPORT"));

        mockMvc.perform(get("/api/v1/document-types/{code}", "DEMOLITION_SAFETY_CHECKLIST")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DEMOLITION_SAFETY_CHECKLIST"))
                .andExpect(jsonPath("$.checklistSchemaCode").value("DEMOLITION_SAFETY_CHECKLIST_DEFAULT"))
                .andExpect(jsonPath("$.defaultTemplateStorageRef")
                        .value("templates/korean/korean-demolition-safety-checklist-appendix-1.docx"));

        var reportId = createReport(user, projectId);

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/workflow-definition", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DOCUMENT_TYPE_DEFAULT"))
                .andExpect(jsonPath("$.flowId").value("demolition-safety-checklist-writing"))
                .andExpect(jsonPath("$.checklistSchemaCode").value("DEMOLITION_SAFETY_CHECKLIST_DEFAULT"))
                .andExpect(jsonPath("$.steps[0].code").value("BASIC_INFO"))
                .andExpect(jsonPath("$.steps[1].code").value("CHECKLIST"));

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/checklist", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema.code").value("DEMOLITION_SAFETY_CHECKLIST_DEFAULT"))
                .andExpect(jsonPath("$.schema.items[0].itemCode").value("DEMOLITION_SEQUENCE"));
    }

    private boolean hasCode(JsonNode list, String code) {
        return StreamSupport.stream(list.spliterator(), false)
                .anyMatch(item -> code.equals(item.get("code").asText()));
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
                                  "name": "Demolition Project",
                                  "buildingType": "CONSTRUCTION_SUPERVISION"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long createReport(TestUser user, long projectId) throws Exception {
        var result = mockMvc.perform(post("/api/v1/inspection-reports")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "reportType": "demolition_safety_checklist"
                                }
                                """.formatted(projectId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportType").value("DEMOLITION_SAFETY_CHECKLIST"))
                .andExpect(jsonPath("$.title").value("\uD574\uCCB4\uACF5\uC0AC \uC548\uC804\uC810\uAC80\uD45C"))
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long readId(String json) throws Exception {
        return objectMapper.readTree(json).get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(long officeId, String accessToken) {
    }
}

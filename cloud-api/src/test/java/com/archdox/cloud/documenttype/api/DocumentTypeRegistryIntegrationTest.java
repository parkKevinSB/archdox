package com.archdox.cloud.documenttype.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Autowired
    JdbcTemplate jdbcTemplate;

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
        assertEquals(2, documentTypeList.size());
        assertTrue(hasCode(documentTypeList, "CONSTRUCTION_DAILY_SUPERVISION_LOG"));
        assertTrue(hasCode(documentTypeList, "CONSTRUCTION_SUPERVISION_REPORT"));
        assertFalse(hasCode(documentTypeList, "DEMOLITION_SAFETY_CHECKLIST"));
        assertFalse(hasCode(documentTypeList, "DAILY_SUPERVISION"));
        assertFalse(hasCode(documentTypeList, "PERIODIC_SAFETY"));
        assertFalse(hasCode(documentTypeList, "FACILITY_CHECK"));
        assertEquals(2L, tableCount("document_type_definitions"));
        assertEquals(2L, tableCount("checklist_schemas"));
        assertEquals(0L, deferredDocumentTypeCount());

        jdbcTemplate.update("""
                insert into document_type_definitions (
                    office_id, code, report_type, name, description, category,
                    default_template_code, default_template_storage_ref,
                    checklist_schema_code, default_output_format, workflow_json,
                    output_layout_json, active, display_order, created_at, updated_at
                )
                values (?, 'DAILY_SUPERVISION', 'DAILY_SUPERVISION', 'Legacy daily report',
                    'Legacy row that should not be exposed.', 'CONSTRUCTION_SUPERVISION',
                    null, null, null, 'DOCX', '{}'::jsonb, '{}'::jsonb, true, 999, now(), now())
                """, user.officeId());
        jdbcTemplate.update("""
                insert into document_type_definitions (
                    office_id, code, report_type, name, description, category,
                    default_template_code, default_template_storage_ref,
                    checklist_schema_code, default_output_format, workflow_json,
                    output_layout_json, active, display_order, created_at, updated_at
                )
                values (?, 'CONSTRUCTION_DAILY_SUPERVISION_LOG', 'CONSTRUCTION_DAILY_SUPERVISION_LOG', '감리일지 legacy',
                    'Office-specific document type rows must not override the MVP canonical document type.', 'CONSTRUCTION_SUPERVISION',
                    null, null, null, 'DOCX', '{}'::jsonb, '{}'::jsonb, true, 998, now(), now())
                """, user.officeId());

        var afterOfficeLegacyInsert = mockMvc.perform(get("/api/v1/document-types")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andReturn();
        var afterOfficeLegacyList = objectMapper.readTree(afterOfficeLegacyInsert.getResponse().getContentAsString());
        assertEquals(2, afterOfficeLegacyList.size());
        assertFalse(hasCode(afterOfficeLegacyList, "DAILY_SUPERVISION"));
        assertFalse(hasName(afterOfficeLegacyList, "감리일지 legacy"));

        mockMvc.perform(get("/api/v1/document-types/{code}", "CONSTRUCTION_SUPERVISION_REPORT")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CONSTRUCTION_SUPERVISION_REPORT"))
                .andExpect(jsonPath("$.checklistSchemaCode").value("CONSTRUCTION_SUPERVISION_REPORT_DEFAULT"))
                .andExpect(jsonPath("$.defaultTemplateStorageRef")
                        .value("templates/korean/korean-construction-supervision-report-appendix-1.docx"));

        mockMvc.perform(get("/api/v1/document-types/{code}", "CONSTRUCTION_DAILY_SUPERVISION_LOG")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("공사감리일지"));

        mockMvc.perform(get("/api/v1/document-types/{code}", "DAILY_SUPERVISION")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isNotFound());

        var reportId = createReport(user, projectId);

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/workflow-definition", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DOCUMENT_TYPE_DEFAULT"))
                .andExpect(jsonPath("$.flowId").value("construction-supervision-report-writing"))
                .andExpect(jsonPath("$.checklistSchemaCode").value("CONSTRUCTION_SUPERVISION_REPORT_DEFAULT"))
                .andExpect(jsonPath("$.steps[0].code").value("BASIC_INFO"))
                .andExpect(jsonPath("$.steps[1].code").value("REPORT_OPINION"))
                .andExpect(jsonPath("$.steps[2].code").value("REMARKS"))
                .andExpect(jsonPath("$.steps[3].code").value("PHOTOS"));

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/checklist", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema.code").value("CONSTRUCTION_SUPERVISION_REPORT_DEFAULT"))
                .andExpect(jsonPath("$.schema.items[0].itemCode").value("FIELD_INVESTIGATION"));

        var constructionDailyWorkflow = documentTypeJson("CONSTRUCTION_DAILY_SUPERVISION_LOG", "workflow_json");
        assertTrue(hasStepType(constructionDailyWorkflow, "DAILY_LOG", "DAILY_SUPERVISION_ITEMS"));
        assertTrue(hasStepField(constructionDailyWorkflow, "DAILY_LOG", "dailyItems"));
        assertTrue(hasStepField(constructionDailyWorkflow, "REMARKS", "issueAndAction"));

        mockMvc.perform(get("/api/v1/supervision-domain-catalogs/{catalogCode}",
                        "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogCode").value("CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.source.revisionLabel").value("개정 2020. 12. 24."))
                .andExpect(jsonPath("$.coverage.tradeCount").value(46))
                .andExpect(jsonPath("$.coverage.detailGroupCount").value(49))
                .andExpect(jsonPath("$.documentLayoutPolicy.defaultOfficialLayout.layoutVersion").value(1))
                .andExpect(jsonPath("$.trades[0].code").value("TEMPORARY_WORKS"))
                .andExpect(jsonPath("$.trades.length()").value(46))
                .andExpect(jsonPath("$.trades[4].code").value("REINFORCED_CONCRETE"))
                .andExpect(jsonPath("$.trades[4].processGroups[0].code").value("REBAR_ASSEMBLY"))
                .andExpect(jsonPath("$.trades[4].processGroups[0].items[0].code")
                        .value("RC_REBAR_COUNT_DIAMETER_PITCH"))
                .andExpect(jsonPath("$.trades[45].code").value("ELECTRICAL_FIRE_FIGHTING"));

        var constructionReportLayout = documentTypeJson("CONSTRUCTION_SUPERVISION_REPORT", "output_layout_json");
        assertTrue(hasSection(constructionReportLayout, "reportOpinionSection", "CHECKLIST_TABLE"));
        assertTrue(hasSection(constructionReportLayout, "photoSection", "PHOTO_TABLE"));

        mockMvc.perform(post("/api/v1/inspection-reports")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "reportType": "DAILY_SUPERVISION",
                                  "title": "Legacy daily report"
                                }
                                """.formatted(projectId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NOT_SUPPORTED"));

        mockMvc.perform(post("/api/v1/inspection-reports")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "reportType": "DEMOLITION_SAFETY_CHECKLIST",
                                  "title": "Deferred demolition report"
                                }
                                """.formatted(projectId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NOT_SUPPORTED"));
    }

    private boolean hasCode(JsonNode list, String code) {
        return StreamSupport.stream(list.spliterator(), false)
                .anyMatch(item -> code.equals(item.get("code").asText()));
    }

    private boolean hasName(JsonNode list, String name) {
        return StreamSupport.stream(list.spliterator(), false)
                .anyMatch(item -> name.equals(item.get("name").asText()));
    }

    private JsonNode documentTypeJson(String code, String column) throws Exception {
        var json = jdbcTemplate.queryForObject(
                "select " + column + "::text from document_type_definitions where office_id is null and code = ?",
                String.class,
                code);
        return objectMapper.readTree(json);
    }

    private long tableCount(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
    }

    private long deferredDocumentTypeCount() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from document_type_definitions
                where code not in ('CONSTRUCTION_DAILY_SUPERVISION_LOG', 'CONSTRUCTION_SUPERVISION_REPORT')
                   or report_type not in ('CONSTRUCTION_DAILY_SUPERVISION_LOG', 'CONSTRUCTION_SUPERVISION_REPORT')
                   or category <> 'CONSTRUCTION_SUPERVISION'
                """, Long.class);
    }

    private boolean hasStepField(JsonNode workflow, String stepCode, String fieldKey) {
        return StreamSupport.stream(workflow.path("steps").spliterator(), false)
                .filter(step -> stepCode.equals(step.path("code").asText()))
                .flatMap(step -> StreamSupport.stream(step.path("fields").spliterator(), false))
                .anyMatch(field -> fieldKey.equals(field.path("key").asText()));
    }

    private boolean hasStepType(JsonNode workflow, String stepCode, String stepType) {
        return StreamSupport.stream(workflow.path("steps").spliterator(), false)
                .anyMatch(step -> stepCode.equals(step.path("code").asText())
                        && stepType.equals(step.path("stepType").asText()));
    }

    private boolean hasSection(JsonNode outputLayout, String key, String type) {
        return StreamSupport.stream(outputLayout.path("sections").spliterator(), false)
                .anyMatch(section -> key.equals(section.path("key").asText())
                        && type.equals(section.path("type").asText()));
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
                                  "name": "Construction Project",
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
                                  "reportType": "construction_supervision_report"
                                }
                                """.formatted(projectId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportType").value("CONSTRUCTION_SUPERVISION_REPORT"))
                .andExpect(jsonPath("$.title").value("\uAC10\uB9AC\uBCF4\uACE0\uC11C"))
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

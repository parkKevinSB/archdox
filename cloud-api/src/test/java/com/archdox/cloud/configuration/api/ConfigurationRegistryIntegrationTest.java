package com.archdox.cloud.configuration.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class ConfigurationRegistryIntegrationTest {
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
    void officeAdminCanReadDocumentTemplateFieldCatalog() throws Exception {
        var alpha = signup("config-fields@example.com", "Config Fields");

        var result = mockMvc.perform(get("/api/v1/config/document-template-fields")
                        .header("Authorization", bearer(alpha.accessToken()))
                        .header("X-Office-Id", alpha.officeId())
                        .param("reportType", "construction_daily_supervision_log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportType").value("CONSTRUCTION_DAILY_SUPERVISION_LOG"))
                .andExpect(jsonPath("$.fields").isArray())
                .andExpect(jsonPath("$.presets").isArray())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(hasField(body, "constructionName"));
        assertTrue(hasField(body, "constructionTrade"));
        assertFalse(hasField(body, "demolitionWorkerName"));
        assertTrue(hasPreset(body, "KOREAN_CONSTRUCTION_DAILY_SUPERVISION_APPENDIX_2"));
        assertFalse(hasPreset(body, "OFFICE_INTERNAL_CONSTRUCTION_DAILY_SUPERVISION"));
        var officialPreset = preset(body, "KOREAN_CONSTRUCTION_DAILY_SUPERVISION_APPENDIX_2");
        assertTrue(officialPreset.get("templateKind").asText().equals("OFFICIAL_SUBMISSION"));
        assertTrue(officialPreset.get("renderingPolicy").asText().equals("BUNDLED_OFFICIAL_RENDERER"));
    }

    @Test
    void officeAdminCanCreatePublishOverrideAndResolveConfiguration() throws Exception {
        var alpha = signup("config-alpha@example.com", "Config Alpha");
        var bravo = signup("config-bravo@example.com", "Config Bravo");

        var templateId = createDefinition(
                alpha,
                "/api/v1/config/document-templates",
                "daily-template",
                "Daily Template",
                "construction_daily_supervision_log");
        var templateRevisionId = createTemplateRevision(alpha, templateId);
        publish(alpha, "/api/v1/config/document-template-revisions/" + templateRevisionId + "/publish");

        var workflowId = createDefinition(
                alpha,
                "/api/v1/config/workflow-definitions",
                "daily-flow",
                "Daily Flow",
                "construction_daily_supervision_log");
        var workflowRevisionId = createJsonRevision(
                alpha,
                "/api/v1/config/workflow-definitions/" + workflowId + "/revisions",
                """
                        {
                          "payload": {
                            "workflow": ["PHOTO_UPLOAD", "PDF_GENERATE"]
                          }
                        }
                        """);
        publish(alpha, "/api/v1/config/workflow-definition-revisions/" + workflowRevisionId + "/publish");

        var ruleSetId = createDefinition(
                alpha,
                "/api/v1/config/rule-sets",
                "daily-rules",
                "Daily Rules",
                "construction_daily_supervision_log");
        var ruleSetRevisionId = createJsonRevision(
                alpha,
                "/api/v1/config/rule-sets/" + ruleSetId + "/revisions",
                """
                        {
                          "payload": {
                            "minPhotos": 3
                          }
                        }
                        """);
        publish(alpha, "/api/v1/config/rule-set-revisions/" + ruleSetRevisionId + "/publish");

        var layoutId = createDefinition(
                alpha,
                "/api/v1/config/output-layouts",
                "daily-layout",
                "Daily Layout",
                "construction_daily_supervision_log");
        var layoutRevisionId = createJsonRevision(
                alpha,
                "/api/v1/config/output-layouts/" + layoutId + "/revisions",
                """
                        {
                          "payload": {
                            "sections": [
                              {
                                "type": "photoTable",
                                "photosPerRow": 2
                              }
                            ]
                          }
                        }
                        """);
        publish(alpha, "/api/v1/config/output-layout-revisions/" + layoutRevisionId + "/publish");

        var overrideBody = """
                {
                  "templateRevisionId": %d,
                  "workflowRevisionId": %d,
                  "ruleSetRevisionId": %d,
                  "outputLayoutRevisionId": %d
                }
                """.formatted(templateRevisionId, workflowRevisionId, ruleSetRevisionId, layoutRevisionId);
        mockMvc.perform(put("/api/v1/config/office-overrides/{reportType}", "construction_daily_supervision_log")
                        .header("Authorization", bearer(alpha.accessToken()))
                        .header("X-Office-Id", alpha.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportType").value("CONSTRUCTION_DAILY_SUPERVISION_LOG"))
                .andExpect(jsonPath("$.template.revisionId").value(templateRevisionId))
                .andExpect(jsonPath("$.workflow.revisionId").value(workflowRevisionId))
                .andExpect(jsonPath("$.ruleSet.revisionId").value(ruleSetRevisionId))
                .andExpect(jsonPath("$.outputLayout.revisionId").value(layoutRevisionId));

        mockMvc.perform(get("/api/v1/config/resolve")
                        .header("Authorization", bearer(alpha.accessToken()))
                        .header("X-Office-Id", alpha.officeId())
                        .param("reportType", "construction_daily_supervision_log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template.source").value("OFFICE_OVERRIDE"))
                .andExpect(jsonPath("$.template.revisionId").value(templateRevisionId))
                .andExpect(jsonPath("$.workflow.source").value("OFFICE_OVERRIDE"))
                .andExpect(jsonPath("$.ruleSet.source").value("OFFICE_OVERRIDE"))
                .andExpect(jsonPath("$.outputLayout.source").value("OFFICE_OVERRIDE"));

        mockMvc.perform(put("/api/v1/config/office-overrides/{reportType}", "construction_daily_supervision_log")
                        .header("Authorization", bearer(bravo.accessToken()))
                        .header("X-Office-Id", bravo.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideBody))
                .andExpect(status().isNotFound());
    }

    private long createDefinition(TestUser user, String path, String code, String name, String reportType) throws Exception {
        var body = """
                {
                  "code": "%s",
                  "name": "%s",
                  "reportType": "%s"
                }
                """.formatted(code, name, reportType);
        var result = mockMvc.perform(post(path)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code.toUpperCase(java.util.Locale.ROOT)))
                .andExpect(jsonPath("$.reportType").value("CONSTRUCTION_DAILY_SUPERVISION_LOG"))
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long createTemplateRevision(TestUser user, long templateId) throws Exception {
        var body = """
                {
                  "templateStorageKind": "API_LOCAL",
                  "templateStorageRef": "templates/daily.docx",
                  "schema": {
                    "required": ["projectName"]
                  },
                  "composePolicy": {
                    "photoSection": "photoTable"
                  },
                  "aiPrompts": {}
                }
                """;
        var result = mockMvc.perform(post("/api/v1/config/document-templates/{templateId}/revisions", templateId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long createJsonRevision(TestUser user, String path, String body) throws Exception {
        var result = mockMvc.perform(post(path)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private void publish(TestUser user, String path) throws Exception {
        mockMvc.perform(post(path)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
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
        var userId = me.get("id").asLong();
        var officeId = me.get("offices").get(0).get("id").asLong();
        return new TestUser(userId, officeId, accessToken);
    }

    private long readId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }

    private boolean hasField(JsonNode body, String key) {
        for (JsonNode field : body.get("fields")) {
            if (key.equals(field.get("key").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPreset(JsonNode body, String code) {
        for (JsonNode preset : body.get("presets")) {
            if (code.equals(preset.get("code").asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode preset(JsonNode body, String code) {
        for (JsonNode preset : body.get("presets")) {
            if (code.equals(preset.get("code").asText())) {
                return preset;
            }
        }
        throw new IllegalArgumentException("Preset not found: " + code);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(long userId, long officeId, String accessToken) {
    }
}

package com.archdox.cloud.site.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class SiteIntegrationTest {
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
    void createsListsAndArchivesSitesUnderProject() throws Exception {
        var user = signup("site-owner@example.com", "Site Owner");
        var projectId = createProject(user, "Safety Contract");

        var siteId = createSite(user, projectId, "Main Site");

        mockMvc.perform(get("/api/v1/projects/{projectId}/sites", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(siteId))
                .andExpect(jsonPath("$[0].name").value("Main Site"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/sites/{siteId}/archive", projectId, siteId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void reportCanCarrySiteContext() throws Exception {
        var user = signup("site-report@example.com", "Site Report");
        var projectId = createProject(user, "Building Program");
        var siteId = createSite(user, projectId, "Building A");

        var body = """
                {
                  "projectId": %d,
                  "siteId": %d,
                  "reportType": "PERIODIC_SAFETY",
                  "title": "Quarterly Safety Check"
                }
                """.formatted(projectId, siteId);

        mockMvc.perform(post("/api/v1/inspection-reports")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.siteId").value(siteId))
                .andExpect(jsonPath("$.reportType").value("PERIODIC_SAFETY"));
    }

    @Test
    void savedReportStepsCanBeListedForWizardResume() throws Exception {
        var user = signup("site-step@example.com", "Site Step");
        var projectId = createProject(user, "Step Project");
        var siteId = createSite(user, projectId, "Step Site");
        var reportId = createReport(user, projectId, siteId);

        var body = """
                {
                  "payload": {
                    "inspectionDate": "2026-05-22",
                    "weather": "SUNNY",
                    "inspectorName": "Site Step"
                  }
                }
                """;

        mockMvc.perform(put("/api/v1/inspection-reports/{reportId}/steps/{stepCode}", reportId, "BASIC_INFO")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepCode").value("BASIC_INFO"))
                .andExpect(jsonPath("$.payload.weather").value("SUNNY"));

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}/steps", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].stepCode").value("BASIC_INFO"))
                .andExpect(jsonPath("$[0].payload.inspectorName").value("Site Step"));
    }

    @Test
    void userCannotAccessAnotherOfficeSite() throws Exception {
        var userA = signup("site-alpha@example.com", "Site Alpha");
        var userB = signup("site-bravo@example.com", "Site Bravo");
        var projectB = createProject(userB, "Bravo Project");
        var siteB = createSite(userB, projectB, "Bravo Site");

        mockMvc.perform(get("/api/v1/projects/{projectId}/sites/{siteId}", projectB, siteB)
                        .header("Authorization", bearer(userA.accessToken()))
                        .header("X-Office-Id", userA.officeId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/projects/{projectId}/sites/{siteId}", projectB, siteB)
                        .header("Authorization", bearer(userA.accessToken()))
                        .header("X-Office-Id", userB.officeId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnsupportedProjectAndSiteTypes() throws Exception {
        var user = signup("site-type@example.com", "Site Type");

        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Invalid Type Project",
                                  "buildingType": "FREE_TEXT_TYPE"
                                }
                                """))
                .andExpect(status().isBadRequest());

        var projectId = createProject(user, "Valid Type Project");
        mockMvc.perform(post("/api/v1/projects/{projectId}/sites", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Invalid Type Site",
                                  "siteType": "FREE_TEXT_SITE"
                                }
                                """))
                .andExpect(status().isBadRequest());
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
        var officeId = me.get("offices").get(0).get("id").asLong();
        return new TestUser(officeId, accessToken);
    }

    private long createProject(TestUser user, String name) throws Exception {
        var result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long createSite(TestUser user, long projectId, String name) throws Exception {
        var body = """
                {
                  "siteCode": "SITE-1",
                  "name": "%s",
                  "address": "Seoul",
                  "siteType": "BUILDING"
                }
                """.formatted(name);
        var result = mockMvc.perform(post("/api/v1/projects/{projectId}/sites", projectId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long createReport(TestUser user, long projectId, long siteId) throws Exception {
        var body = """
                {
                  "projectId": %d,
                  "siteId": %d,
                  "reportType": "DAILY_SUPERVISION",
                  "title": "Daily report"
                }
                """.formatted(projectId, siteId);
        var result = mockMvc.perform(post("/api/v1/inspection-reports")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
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

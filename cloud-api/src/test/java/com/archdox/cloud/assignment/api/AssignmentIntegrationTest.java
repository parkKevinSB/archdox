package com.archdox.cloud.assignment.api;

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
class AssignmentIntegrationTest {
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
    void projectAndReportAssignmentsNarrowMemberWriteAccessWhenPresent() throws Exception {
        var owner = signup("assign-owner@example.com", "Assign Owner");
        var manager = signup("assign-manager@example.com", "Assign Manager");
        var writer = signup("assign-writer@example.com", "Assign Writer");
        var otherMember = signup("assign-other@example.com", "Assign Other");
        var viewer = signup("assign-viewer@example.com", "Assign Viewer");
        var officeId = createOffice(owner, "Assignment Architecture Office");

        addMember(owner, officeId, manager.email(), "MEMBER");
        addMember(owner, officeId, writer.email(), "MEMBER");
        addMember(owner, officeId, otherMember.email(), "MEMBER");
        addMember(owner, officeId, viewer.email(), "VIEWER");

        var projectId = createProject(owner.accessToken(), officeId, "Assigned Project");

        createReport(otherMember.accessToken(), officeId, projectId, "Before assignments")
                .andExpect(status().isForbidden());

        upsertProjectAssignment(owner, officeId, projectId, writer.userId(), "REPORT_WRITER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(writer.userId()))
                .andExpect(jsonPath("$.role").value("REPORT_WRITER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        createReport(otherMember.accessToken(), officeId, projectId, "Blocked after project assignment")
                .andExpect(status().isForbidden());

        var reportId = readId(createReport(writer.accessToken(), officeId, projectId, "Writer report")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        upsertReportAssignment(owner, officeId, reportId, otherMember.userId(), "WRITER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(otherMember.userId()))
                .andExpect(jsonPath("$.role").value("WRITER"));

        getReport(writer.accessToken(), officeId, reportId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writeAllowed").value(false))
                .andExpect(jsonPath("$.reopenAllowed").value(false));

        getReport(otherMember.accessToken(), officeId, reportId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writeAllowed").value(true));

        saveStep(writer.accessToken(), officeId, reportId)
                .andExpect(status().isForbidden());

        saveStep(otherMember.accessToken(), officeId, reportId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepCode").value("BASIC_INFO"));

        upsertProjectAssignment(owner, officeId, projectId, manager.userId(), "MANAGER")
                .andExpect(status().isOk());

        createSite(manager.accessToken(), officeId, projectId, "Manager Site")
                .andExpect(status().isCreated());

        createSite(writer.accessToken(), officeId, projectId, "Writer Site")
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/projects/{projectId}/assignments", projectId)
                        .header("Authorization", bearer(viewer.accessToken()))
                        .header("X-Office-Id", officeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == %d && @.role == 'REPORT_WRITER')]".formatted(writer.userId())).exists())
                .andExpect(jsonPath("$[?(@.userId == %d && @.role == 'MANAGER')]".formatted(manager.userId())).exists());
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
        return new TestUser(
                me.get("id").asLong(),
                email,
                me.get("offices").get(0).get("id").asLong(),
                accessToken);
    }

    private long createOffice(TestUser owner, String displayName) throws Exception {
        var result = mockMvc.perform(post("/api/v1/offices")
                        .header("Authorization", bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private void addMember(TestUser actor, long officeId, String email, String role) throws Exception {
        var body = """
                {
                  "email": "%s",
                  "role": "%s"
                }
                """.formatted(email, role);
        mockMvc.perform(post("/api/v1/offices/{officeId}/members", officeId)
                        .header("Authorization", bearer(actor.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private long createProject(String accessToken, long officeId, String name) throws Exception {
        var result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(accessToken))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions upsertProjectAssignment(
            TestUser actor,
            long officeId,
            long projectId,
            long userId,
            String role
    ) throws Exception {
        var body = """
                {
                  "userId": %d,
                  "role": "%s"
                }
                """.formatted(userId, role);
        return mockMvc.perform(put("/api/v1/projects/{projectId}/assignments", projectId)
                .header("Authorization", bearer(actor.accessToken()))
                .header("X-Office-Id", officeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions upsertReportAssignment(
            TestUser actor,
            long officeId,
            long reportId,
            long userId,
            String role
    ) throws Exception {
        var body = """
                {
                  "userId": %d,
                  "role": "%s"
                }
                """.formatted(userId, role);
        return mockMvc.perform(put("/api/v1/inspection-reports/{reportId}/assignments", reportId)
                .header("Authorization", bearer(actor.accessToken()))
                .header("X-Office-Id", officeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions createReport(
            String accessToken,
            long officeId,
            long projectId,
            String title
    ) throws Exception {
        var body = """
                {
                  "projectId": %d,
                  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                  "title": "%s"
                }
                """.formatted(projectId, title);
        return mockMvc.perform(post("/api/v1/inspection-reports")
                .header("Authorization", bearer(accessToken))
                .header("X-Office-Id", officeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions saveStep(
            String accessToken,
            long officeId,
            long reportId
    ) throws Exception {
        return mockMvc.perform(put("/api/v1/inspection-reports/{reportId}/steps/BASIC_INFO", reportId)
                .header("Authorization", bearer(accessToken))
                .header("X-Office-Id", officeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "payload": {
                            "inspectionDate": "2026-05-22"
                          }
                        }
                        """));
    }

    private org.springframework.test.web.servlet.ResultActions getReport(
            String accessToken,
            long officeId,
            long reportId
    ) throws Exception {
        return mockMvc.perform(get("/api/v1/inspection-reports/{reportId}", reportId)
                .header("Authorization", bearer(accessToken))
                .header("X-Office-Id", officeId));
    }

    private org.springframework.test.web.servlet.ResultActions createSite(
            String accessToken,
            long officeId,
            long projectId,
            String name
    ) throws Exception {
        var body = """
                {
                  "name": "%s",
                  "siteType": "BUILDING"
                }
                """.formatted(name);
        return mockMvc.perform(post("/api/v1/projects/{projectId}/sites", projectId)
                .header("Authorization", bearer(accessToken))
                .header("X-Office-Id", officeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long readId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(long userId, String email, long officeId, String accessToken) {
    }
}

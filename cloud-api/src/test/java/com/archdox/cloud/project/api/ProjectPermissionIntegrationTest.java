package com.archdox.cloud.project.api;

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
class ProjectPermissionIntegrationTest {
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
    void officeRolesControlProjectManagementAndReportWriting() throws Exception {
        var owner = signup("perm-owner@example.com", "Permission Owner");
        var admin = signup("perm-admin@example.com", "Permission Admin");
        var member = signup("perm-member@example.com", "Permission Member");
        var viewer = signup("perm-viewer@example.com", "Permission Viewer");
        var officeId = createOffice(owner, "Permission Architecture Office");

        addMember(owner, officeId, admin.email(), "ADMIN");
        addMember(owner, officeId, member.email(), "MEMBER");
        addMember(owner, officeId, viewer.email(), "VIEWER");

        var ownerProjectId = createProject(owner.accessToken(), officeId, "Owner Managed Project");
        createProject(admin.accessToken(), officeId, "Admin Managed Project");

        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(member.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Member Project\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(viewer.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Viewer Project\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", bearer(viewer.accessToken()))
                        .header("X-Office-Id", officeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(ownerProjectId)).exists());

        mockMvc.perform(post("/api/v1/inspection-reports")
                        .header("Authorization", bearer(member.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                                  "title": "Member blocked before assignment"
                                }
                                """.formatted(ownerProjectId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/projects/{projectId}/assignments", ownerProjectId)
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "role": "REPORT_WRITER"
                                }
                                """.formatted(member.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("REPORT_WRITER"));

        mockMvc.perform(post("/api/v1/inspection-reports")
                        .header("Authorization", bearer(member.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                                  "title": "Member writable after assignment"
                                }
                                """.formatted(ownerProjectId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/inspection-reports")
                        .header("Authorization", bearer(viewer.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                                  "title": "Viewer blocked report"
                                }
                                """.formatted(ownerProjectId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/projects/{projectId}/archive", ownerProjectId)
                        .header("Authorization", bearer(member.accessToken()))
                        .header("X-Office-Id", officeId))
                .andExpect(status().isForbidden());
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

package com.archdox.cloud;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class OfficeIsolationIntegrationTest {
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
    void userCannotAccessAnotherOfficeProjectOrReport() throws Exception {
        var userA = signup("alpha@example.com", "Alpha User");
        var userB = signup("bravo@example.com", "Bravo User");

        var projectB = createProject(userB.accessToken(), userB.officeId(), "Bravo Tower");
        var reportB = createReport(userB.accessToken(), userB.officeId(), projectB, "DAILY_SUPERVISION");

        mockMvc.perform(get("/api/v1/projects/{projectId}", projectB)
                        .header("Authorization", bearer(userA.accessToken()))
                        .header("X-Office-Id", userA.officeId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/projects/{projectId}", projectB)
                        .header("Authorization", bearer(userA.accessToken()))
                        .header("X-Office-Id", userB.officeId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}", reportB)
                        .header("Authorization", bearer(userA.accessToken()))
                        .header("X-Office-Id", userA.officeId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}", reportB)
                        .header("Authorization", bearer(userA.accessToken()))
                        .header("X-Office-Id", userB.officeId()))
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
        var userId = me.get("id").asLong();
        var officeId = me.get("offices").get(0).get("id").asLong();
        return new TestUser(userId, officeId, accessToken);
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

    private long createReport(String accessToken, long officeId, long projectId, String reportType) throws Exception {
        var body = """
                {
                  "projectId": %d,
                  "reportType": "%s",
                  "title": "Daily report"
                }
                """.formatted(projectId, reportType);
        var result = mockMvc.perform(post("/api/v1/inspection-reports")
                        .header("Authorization", bearer(accessToken))
                        .header("X-Office-Id", officeId)
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

    private record TestUser(long userId, long officeId, String accessToken) {
    }
}

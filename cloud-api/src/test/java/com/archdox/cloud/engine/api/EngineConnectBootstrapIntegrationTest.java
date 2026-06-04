package com.archdox.cloud.engine.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest(properties = {
        "archdox.engine.connect.engine-api-base-url=https://api.archdox.test",
        "archdox.engine.connect.mcp-server-url=https://mcp.archdox.test"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class EngineConnectBootstrapIntegrationTest {
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
    void userCanBootstrapCodexConnectionAndUseReturnedEngineApiKey() throws Exception {
        var user = signup("engine-connect@example.com", "Engine Connect");

        mockMvc.perform(get("/api/v1/engine/connect/clients")
                        .header("Authorization", bearer(user.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CODEX"));

        var bootstrapBody = """
                {
                  "clientType": "CODEX",
                  "displayName": "Codex personal connection",
                  "officeId": %d
                }
                """.formatted(user.officeId());
        var bootstrapResult = mockMvc.perform(post("/api/v1/engine/connect/bootstrap")
                        .header("Authorization", bearer(user.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bootstrapBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.connectionId").value(org.hamcrest.Matchers.startsWith("eng_conn_")))
                .andExpect(jsonPath("$.clientType").value("CODEX"))
                .andExpect(jsonPath("$.key.status").value("ACTIVE"))
                .andExpect(jsonPath("$.key.expiresAt").exists())
                .andExpect(jsonPath("$.engineApiBaseUrl").value("https://api.archdox.test"))
                .andExpect(jsonPath("$.mcpServerUrl").value("https://mcp.archdox.test"))
                .andReturn();

        var bootstrap = objectMapper.readTree(bootstrapResult.getResponse().getContentAsString());
        var apiKey = bootstrap.get("apiKey").asText();
        org.assertj.core.api.Assertions.assertThat(apiKey).startsWith("adx_live_");

        mockMvc.perform(post("/api/v1/engine/external/review-sessions")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewPurpose\":\"preflight\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewSessionId").value(org.hamcrest.Matchers.startsWith("rvw_sess_")));
    }

    @Test
    void userCannotBootstrapConnectionForAnotherUsersOffice() throws Exception {
        var alpha = signup("engine-connect-alpha@example.com", "Engine Alpha");
        var bravo = signup("engine-connect-bravo@example.com", "Engine Bravo");

        var bootstrapBody = """
                {
                  "clientType": "CURSOR",
                  "officeId": %d
                }
                """.formatted(bravo.officeId());
        mockMvc.perform(post("/api/v1/engine/connect/bootstrap")
                        .header("Authorization", bearer(alpha.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bootstrapBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENGINE_CONNECT_OFFICE_ACCESS_DENIED"));
    }

    @Test
    void userCannotRequestBootstrapKeyBeyondMaxTtl() throws Exception {
        var user = signup("engine-connect-ttl@example.com", "Engine TTL");

        var bootstrapBody = """
                {
                  "clientType": "CUSTOM_AGENT",
                  "officeId": %d,
                  "expiresAt": "2099-01-01T00:00:00Z"
                }
                """.formatted(user.officeId());
        mockMvc.perform(post("/api/v1/engine/connect/bootstrap")
                        .header("Authorization", bearer(user.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bootstrapBody))
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
        JsonNode me = objectMapper.readTree(meResult.getResponse().getContentAsString());
        return new TestUser(
                me.get("id").asLong(),
                me.get("offices").get(0).get("id").asLong(),
                accessToken);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(long userId, long officeId, String accessToken) {
    }
}

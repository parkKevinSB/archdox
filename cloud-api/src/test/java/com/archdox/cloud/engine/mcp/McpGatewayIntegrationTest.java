package com.archdox.cloud.engine.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.archdox.cloud.engine.auth.application.EngineApiKeyManagementService;
import com.archdox.cloud.engine.usage.application.EngineApiUsageService;
import com.archdox.cloud.engine.usage.domain.EngineApiUsageEvent;
import com.archdox.cloud.engine.usage.infra.EngineApiUsageEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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

@SpringBootTest(properties = {
        "archdox.engine.connect.mcp-server-url=https://mcp.archdox.test/api/v1/mcp"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class McpGatewayIntegrationTest {
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
    EngineApiUsageEventRepository usageEventRepository;

    @Autowired
    EngineApiKeyManagementService apiKeyManagementService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void mcpGatewayListsAndCallsEngineToolsWithEngineApiKey() throws Exception {
        var user = signup("mcp-user@example.com", "MCP User");
        var apiKey = bootstrapApiKey(user.accessToken(), user.officeId());
        var legalArticleVersionId = seedLegalCorpus();

        mockMvc.perform(post("/api/v1/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/mcp")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-06-18"))
                .andExpect(jsonPath("$.result.capabilities.tools.listChanged").value(false));

        mockMvc.perform(post("/api/v1/mcp")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"resources/list\",\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32601))
                .andExpect(jsonPath("$.error.data.code").value("MCP_METHOD_NOT_FOUND"))
                .andExpect(jsonPath("$.error.data.category").value("METHOD_NOT_FOUND"))
                .andExpect(jsonPath("$.error.data.retryable").value(false));

        mockMvc.perform(post("/api/v1/mcp")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools[*].name")
                        .value(org.hamcrest.Matchers.hasItems(
                                "validate_inspection_report",
                                "get_legal_updates",
                                "search_law",
                                "get_law_article")));

        mockMvc.perform(post("/api/v1/mcp")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32602))
                .andExpect(jsonPath("$.error.data.code").value("MCP_INVALID_PARAMS"))
                .andExpect(jsonPath("$.error.data.category").value("INVALID_PARAMS"))
                .andExpect(jsonPath("$.error.data.retryable").value(false));

        mockMvc.perform(post("/api/v1/mcp")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": 5,
                                  "method": "tools/call",
                                  "params": {
                                    "name": "unknown_tool",
                                    "arguments": {}
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32602))
                .andExpect(jsonPath("$.error.data.code").value("MCP_TOOL_NOT_FOUND"))
                .andExpect(jsonPath("$.error.data.category").value("TOOL_NOT_FOUND"))
                .andExpect(jsonPath("$.error.data.retryable").value(false));

        mockMvc.perform(post("/api/v1/mcp")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .header("X-Correlation-Id", "mcp-legal-success")
                        .header("User-Agent", "ArchDox MCP Integration Test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": 6,
                                  "method": "tools/call",
                                  "params": {
                                    "name": "get_legal_updates",
                                    "arguments": {
                                      "days": 30,
                                      "limit": 10
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.structuredContent.count").exists())
                .andExpect(jsonPath("$.result.structuredContent.items").isArray());

        assertThat(usageEventRepository.findAll())
                .filteredOn(event -> EngineApiUsageService.CAPABILITY_LEGAL_UPDATES.equals(event.capability()))
                .filteredOn(event -> "MCP_GET_LEGAL_UPDATES".equals(event.operation()))
                .filteredOn(event -> EngineApiUsageService.STATUS_SUCCEEDED.equals(event.status()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.requestUnits()).isEqualTo(1);
                    assertThat(event.metadataJson()).containsEntry("toolName", "get_legal_updates");
                    assertThat(event.metadataJson()).containsEntry("correlationId", "mcp-legal-success");
                    assertThat(event.metadataJson()).containsEntry("userAgent", "ArchDox MCP Integration Test");
                });

        mockMvc.perform(post("/api/v1/mcp")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .header("X-Correlation-Id", "mcp-law-search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": 61,
                                  "method": "tools/call",
                                  "params": {
                                    "name": "search_law",
                                    "arguments": {
                                      "query": "감리",
                                      "actCode": "BUILDING_ACT_MCP",
                                      "limit": 5
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.structuredContent.count").value(1))
                .andExpect(jsonPath("$.result.structuredContent.items[0].actCode").value("BUILDING_ACT_MCP"))
                .andExpect(jsonPath("$.result.structuredContent.items[0].articleNo").value("25의2"))
                .andExpect(jsonPath("$.result.structuredContent.items[0].referenceId").value("BUILDING_ACT_MCP:0025001@001823:20260701"))
                .andExpect(jsonPath("$.result.structuredContent.items[0].publicSourceUrl").value("https://www.law.go.kr/%EB%B2%95%EB%A0%B9/%EA%B1%B4%EC%B6%95%EB%B2%95"))
                .andExpect(jsonPath("$.result.structuredContent.items[0].snippet").value(org.hamcrest.Matchers.containsString("감리")));

        mockMvc.perform(post("/api/v1/mcp")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .header("X-Correlation-Id", "mcp-law-article")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": 62,
                                  "method": "tools/call",
                                  "params": {
                                    "name": "get_law_article",
                                    "arguments": {
                                      "articleVersionId": %d
                                    }
                                  }
                                }
                                """.formatted(legalArticleVersionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.structuredContent.articleVersionId").value(legalArticleVersionId))
                .andExpect(jsonPath("$.result.structuredContent.referenceId").value("BUILDING_ACT_MCP:0025001@001823:20260701"))
                .andExpect(jsonPath("$.result.structuredContent.publicSourceUrl").value("https://www.law.go.kr/%EB%B2%95%EB%A0%B9/%EA%B1%B4%EC%B6%95%EB%B2%95"))
                .andExpect(jsonPath("$.result.structuredContent.articleText").value(org.hamcrest.Matchers.containsString("감리자는")));

        mockMvc.perform(get("/api/v1/engine/external/legal/search")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .queryParam("query", "감리")
                        .queryParam("actCode", "BUILDING_ACT_MCP")
                        .queryParam("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].referenceId").value("BUILDING_ACT_MCP:0025001@001823:20260701"))
                .andExpect(jsonPath("$.items[0].contentHash").value("article-hash"))
                .andExpect(jsonPath("$.items[0].publicSourceUrl").value("https://www.law.go.kr/%EB%B2%95%EB%A0%B9/%EA%B1%B4%EC%B6%95%EB%B2%95"));

        mockMvc.perform(get("/api/v1/engine/external/legal/articles")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .queryParam("articleVersionId", String.valueOf(legalArticleVersionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId").value("BUILDING_ACT_MCP:0025001@001823:20260701"))
                .andExpect(jsonPath("$.articleText").value(org.hamcrest.Matchers.containsString("감리자는")))
                .andExpect(jsonPath("$.publicSourceUrl").value("https://www.law.go.kr/%EB%B2%95%EB%A0%B9/%EA%B1%B4%EC%B6%95%EB%B2%95"));

        assertThat(usageEventRepository.findAll())
                .filteredOn(event -> EngineApiUsageService.CAPABILITY_LEGAL_SEARCH.equals(event.capability()))
                .filteredOn(event -> EngineApiUsageService.STATUS_SUCCEEDED.equals(event.status()))
                .extracting(EngineApiUsageEvent::operation)
                .contains("MCP_SEARCH_LAW", "MCP_GET_LAW_ARTICLE", "REST_SEARCH_LAW", "REST_GET_LAW_ARTICLE");

        var updatesOnlyKey = issueApiKey(user, List.of(EngineApiKeyManagementService.SCOPE_LEGAL_UPDATES), 1000);
        mockMvc.perform(post("/api/v1/mcp")
                        .header("X-ArchDox-Engine-Key", updatesOnlyKey)
                        .header("X-Correlation-Id", "mcp-law-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": 63,
                                  "method": "tools/call",
                                  "params": {
                                    "name": "search_law",
                                    "arguments": {
                                      "query": "감리"
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32003))
                .andExpect(jsonPath("$.error.data.code").value("ENGINE_API_SCOPE_REQUIRED"))
                .andExpect(jsonPath("$.error.data.category").value("SCOPE_REQUIRED"))
                .andExpect(jsonPath("$.error.data.params.requiredScope").value("LEGAL_SEARCH"));

        var reviewOnlyKey = issueApiKey(user, List.of(EngineApiKeyManagementService.SCOPE_REVIEW_SESSION), 1000);
        mockMvc.perform(post("/api/v1/mcp")
                        .header("X-ArchDox-Engine-Key", reviewOnlyKey)
                        .header("X-Correlation-Id", "mcp-legal-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": 7,
                                  "method": "tools/call",
                                  "params": {
                                    "name": "get_legal_updates",
                                    "arguments": {}
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32003))
                .andExpect(jsonPath("$.error.data.code").value("ENGINE_API_SCOPE_REQUIRED"))
                .andExpect(jsonPath("$.error.data.category").value("SCOPE_REQUIRED"))
                .andExpect(jsonPath("$.error.data.retryable").value(false))
                .andExpect(jsonPath("$.error.data.params.requiredScope").value("LEGAL_UPDATES"));

        assertThat(usageEventRepository.findAll())
                .filteredOn(event -> EngineApiUsageService.CAPABILITY_LEGAL_UPDATES.equals(event.capability()))
                .filteredOn(event -> EngineApiUsageService.STATUS_DENIED.equals(event.status()))
                .anySatisfy(event -> {
                    assertThat(event.requestUnits()).isEqualTo(0);
                    assertThat(event.metadataJson()).containsEntry("toolName", "get_legal_updates");
                    assertThat(event.metadataJson()).containsEntry("correlationId", "mcp-legal-denied");
                    assertThat(event.metadataJson()).containsEntry("errorCode", "ENGINE_API_SCOPE_REQUIRED");
                });

        var limitedLegalKey = issueApiKey(user, List.of(EngineApiKeyManagementService.SCOPE_LEGAL_UPDATES), 1);
        var legalCall = """
                {
                  "jsonrpc": "2.0",
                  "id": 8,
                  "method": "tools/call",
                  "params": {
                    "name": "get_legal_updates",
                    "arguments": {}
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/mcp")
                        .header("X-ArchDox-Engine-Key", limitedLegalKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(legalCall))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false));
        mockMvc.perform(post("/api/v1/mcp")
                        .header("X-ArchDox-Engine-Key", limitedLegalKey)
                        .header("X-Correlation-Id", "mcp-legal-quota")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(legalCall))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32029))
                .andExpect(jsonPath("$.error.data.code").value("ENGINE_API_DAILY_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.error.data.category").value("QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.error.data.retryable").value(true));

        assertThat(usageEventRepository.findAll())
                .filteredOn(event -> EngineApiUsageService.CAPABILITY_LEGAL_UPDATES.equals(event.capability()))
                .filteredOn(event -> EngineApiUsageService.STATUS_DENIED.equals(event.status()))
                .anySatisfy(event -> {
                    assertThat(event.requestUnits()).isEqualTo(0);
                    assertThat(event.metadataJson()).containsEntry("correlationId", "mcp-legal-quota");
                    assertThat(event.metadataJson()).containsEntry("errorCode", "ENGINE_API_DAILY_QUOTA_EXCEEDED");
                });

        var validationResult = mockMvc.perform(post("/api/v1/mcp")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": 3,
                                  "method": "tools/call",
                                  "params": {
                                    "name": "validate_inspection_report",
                                    "arguments": {
                                      "reviewPurpose": "preflight",
                                      "customerProjectRef": "mcp-smoke",
                                      "facts": [
                                        {"name": "buildingUse", "rawValue": "neighborhood living facility", "source": "CUSTOMER_AGENT_EXTRACTED", "confidence": 0.91},
                                        {"name": "structureType", "rawValue": "RC", "source": "CUSTOMER_SYSTEM", "confidence": 0.98},
                                        {"name": "workType", "rawValue": "foundation concrete placement", "source": "USER_PROVIDED", "confidence": 0.88}
                                      ]
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.structuredContent.reviewSessionId")
                        .value(org.hamcrest.Matchers.startsWith("rvw_sess_")))
                .andExpect(jsonPath("$.result.structuredContent.validationResult.engineRunId")
                        .value(org.hamcrest.Matchers.startsWith("eng_")))
                .andExpect(jsonPath("$.result.structuredContent.validationResult.legalReferences").isArray())
                .andExpect(jsonPath("$.result.structuredContent.validationResult.nextActions[*].code")
                        .value(org.hamcrest.Matchers.hasItem("RESULT_READY")))
                .andExpect(jsonPath("$.result.structuredContent.validationResult.nextActions[0].actionType")
                        .value("STATE"))
                .andReturn();

        var reviewSessionId = objectMapper.readTree(validationResult.getResponse().getContentAsString())
                .at("/result/structuredContent/reviewSessionId")
                .asText();
        var usageEvents = usageEventRepository.findAll().stream()
                .filter(event -> reviewSessionId.equals(event.reviewSessionId()))
                .toList();
        assertThat(usageEvents)
                .extracting(EngineApiUsageEvent::operation)
                .containsExactlyInAnyOrder("CREATE_REVIEW_SESSION", "SUBMIT_FACTS", "RUN_VALIDATION");
        assertThat(usageEvents)
                .allSatisfy(event -> {
                    assertThat(event.capability()).isEqualTo(EngineApiUsageService.CAPABILITY_REVIEW_SESSION);
                    assertThat(event.status()).isEqualTo(EngineApiUsageService.STATUS_SUCCEEDED);
                    assertThat(event.requestUnits()).isEqualTo(1);
                });
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
                me.get("offices").get(0).get("id").asLong(),
                accessToken);
    }

    private String issueApiKey(TestUser user, List<String> scopes, int dailyRequestUnitLimit) {
        return apiKeyManagementService.issue(
                        "MCP scoped integration key",
                        user.userId(),
                        user.officeId(),
                        user.userId(),
                        scopes,
                        dailyRequestUnitLimit,
                        OffsetDateTime.now().plusDays(1))
                .apiKey();
    }

    private String bootstrapApiKey(String accessToken, long officeId) throws Exception {
        var bootstrapBody = """
                {
                  "clientType": "CODEX",
                  "displayName": "Codex MCP smoke",
                  "officeId": %d
                }
                """.formatted(officeId);
        var result = mockMvc.perform(post("/api/v1/engine/connect/bootstrap")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bootstrapBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mcpServerUrl").value("https://mcp.archdox.test/api/v1/mcp"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("apiKey").asText();
    }

    private long seedLegalCorpus() {
        var now = OffsetDateTime.now();
        var sourceId = jdbcTemplate.queryForObject("""
                insert into legal_sources (code, source_type, display_name, base_url, status, metadata_json, created_at, updated_at)
                values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
                returning id
                """, Long.class,
                "NATIONAL_LAW_OPEN_DATA_MCP",
                "LAW_OPEN_API",
                "National Law Open Data MCP",
                "https://www.law.go.kr/DRF",
                "ACTIVE",
                "{}",
                now,
                now);
        var actId = jdbcTemplate.queryForObject("""
                insert into legal_acts (source_id, act_code, act_name, act_type, jurisdiction, source_law_id, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class,
                sourceId,
                "BUILDING_ACT_MCP",
                "건축법",
                "LAW",
                "KR",
                "001823",
                "ACTIVE",
                now,
                now);
        var versionId = jdbcTemplate.queryForObject("""
                insert into legal_versions (act_id, source_version_key, promulgation_date, effective_date, source_url, content_hash, source_metadata_json, captured_at)
                values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                returning id
                """, Long.class,
                actId,
                "001823:20260701",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1),
                "https://www.law.go.kr/DRF/lawService.do?ID=001823",
                "version-hash",
                "{}",
                now);
        var articleId = jdbcTemplate.queryForObject("""
                insert into legal_articles (act_id, article_key, article_no, article_title, parent_article_key, sort_order, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class,
                actId,
                "0025001",
                "25의2",
                "공사감리",
                null,
                25,
                now,
                now);
        return jdbcTemplate.queryForObject("""
                insert into legal_article_versions (
                    article_id, legal_version_id, article_key, article_no, article_title,
                    article_text, normalized_text, content_hash, effective_date, source_metadata_json, created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                returning id
                """, Long.class,
                articleId,
                versionId,
                "0025001",
                "25의2",
                "공사감리",
                "감리자는 건축법에 따라 공사감리 업무를 수행한다.",
                "감리자는 건축법에 따라 공사감리 업무를 수행한다.",
                "article-hash",
                LocalDate.of(2026, 7, 1),
                "{}",
                now);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(long userId, long officeId, String accessToken) {
    }
}

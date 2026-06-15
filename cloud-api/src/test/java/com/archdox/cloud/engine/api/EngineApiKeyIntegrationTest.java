package com.archdox.cloud.engine.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
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
class EngineApiKeyIntegrationTest {
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
    void platformAdminCanReadMcpToolCatalog() throws Exception {
        var platformAdmin = signup("engine-mcp-catalog-admin@example.com", "Engine MCP Catalog Admin");
        grantPlatformAdmin(platformAdmin.userId());

        mockMvc.perform(get("/api/v1/platform-admin/engine/mcp-tools")
                        .header("Authorization", bearer(platformAdmin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.hasItems(
                        "validate_inspection_report",
                        "get_legal_updates",
                        "search_law",
                        "get_law_article",
                        "explain_legal_change")))
                .andExpect(jsonPath("$[?(@.name=='validate_inspection_report')].requiredScope")
                        .value(org.hamcrest.Matchers.hasItem("ENGINE_REVIEW_SESSION")))
                .andExpect(jsonPath("$[?(@.name=='validate_inspection_report')].baseRequestUnits")
                        .value(org.hamcrest.Matchers.hasItem(2)))
                .andExpect(jsonPath("$[?(@.name=='search_law')].capability")
                        .value(org.hamcrest.Matchers.hasItem("LEGAL_SEARCH")))
                .andExpect(jsonPath("$[?(@.name=='get_legal_updates')].gatewayManagedUsage")
                        .value(org.hamcrest.Matchers.hasItem(true)));
    }

    @Test
    void platformAdminMcpLiveSmokeRequiresApiKey() throws Exception {
        var platformAdmin = signup("engine-mcp-smoke-admin@example.com", "Engine MCP Smoke Admin");
        grantPlatformAdmin(platformAdmin.userId());

        mockMvc.perform(post("/api/v1/platform-admin/engine/mcp-smoke")
                        .header("Authorization", bearer(platformAdmin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void userCanManageOwnMcpConnectionKeysAndUsage() throws Exception {
        var owner = signup("engine-self-service-owner@example.com", "Engine Self Service Owner");
        var other = signup("engine-self-service-other@example.com", "Engine Self Service Other");

        mockMvc.perform(get("/api/v1/engine/mcp-tools")
                        .header("Authorization", bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.hasItems(
                        "validate_inspection_report",
                        "get_legal_updates",
                        "search_law")));

        var bootstrapBody = """
                {
                  "clientType": "CODEX",
                  "displayName": "Owner Codex MCP connection",
                  "officeId": %d
                }
                """.formatted(owner.officeId());
        var bootstrapResult = mockMvc.perform(post("/api/v1/engine/connect/bootstrap")
                        .header("Authorization", bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bootstrapBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key.displayName").value("Owner Codex MCP connection"))
                .andExpect(jsonPath("$.key.ownerUserId").value(owner.userId()))
                .andExpect(jsonPath("$.key.officeId").value(owner.officeId()))
                .andExpect(jsonPath("$.key.scopes").value(org.hamcrest.Matchers.hasItems(
                        "ENGINE_REVIEW_SESSION",
                        "LEGAL_UPDATES",
                        "LEGAL_SEARCH")))
                .andExpect(jsonPath("$.apiKey").value(org.hamcrest.Matchers.startsWith("adx_live_")))
                .andExpect(jsonPath("$.suggestedMcpConfig.mcpServers.archdox.url").exists())
                .andReturn();
        var bootstrapped = objectMapper.readTree(bootstrapResult.getResponse().getContentAsString());
        var apiKey = bootstrapped.get("apiKey").asText();
        var apiKeyId = bootstrapped.get("key").get("id").asLong();

        mockMvc.perform(get("/api/v1/engine/api-keys")
                        .header("Authorization", bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(apiKeyId))
                .andExpect(jsonPath("$[0].ownerUserId").value(owner.userId()));

        mockMvc.perform(get("/api/v1/engine/api-keys")
                        .header("Authorization", bearer(other.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        mockMvc.perform(post("/api/v1/engine/api-keys/{apiKeyId}/revoke", apiKeyId)
                        .header("Authorization", bearer(other.accessToken())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/engine/mcp-smoke")
                        .header("Authorization", bearer(other.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"%s\"}".formatted(apiKey)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENGINE_MCP_SMOKE_KEY_OWNER_REQUIRED"));

        mockMvc.perform(post("/api/v1/engine/mcp-smoke")
                        .header("Authorization", bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        var sessionResult = mockMvc.perform(post("/api/v1/engine/external/review-sessions")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerProjectRef\":\"self-service-project\",\"reviewPurpose\":\"preflight\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewSessionId").value(org.hamcrest.Matchers.startsWith("rvw_sess_")))
                .andReturn();
        var reviewSessionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .get("reviewSessionId")
                .asText();

        mockMvc.perform(get("/api/v1/engine/usage/events")
                        .header("Authorization", bearer(owner.accessToken()))
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].apiKeyId").value(apiKeyId))
                .andExpect(jsonPath("$[0].ownerUserId").value(owner.userId()))
                .andExpect(jsonPath("$[0].operation").value("CREATE_REVIEW_SESSION"))
                .andExpect(jsonPath("$[0].reviewSessionId").value(reviewSessionId));

        mockMvc.perform(get("/api/v1/engine/usage/summary")
                        .header("Authorization", bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEventCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalRequestUnits").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.groups[0].ownerUserId").value(owner.userId()));

        mockMvc.perform(get("/api/v1/engine/usage/events")
                        .header("Authorization", bearer(other.accessToken()))
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        mockMvc.perform(post("/api/v1/engine/api-keys/{apiKeyId}/revoke", apiKeyId)
                        .header("Authorization", bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        mockMvc.perform(get("/api/v1/engine/external/review-sessions/{reviewSessionId}", reviewSessionId)
                        .header("X-ArchDox-Engine-Key", apiKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ENGINE_API_KEY_INVALID"));
    }

    @Test
    void platformAdminCanIssueAndRevokeExternalEngineApiKey() throws Exception {
        var owner = signup("engine-owner@example.com", "Engine Owner");
        var platformAdmin = signup("engine-platform-admin@example.com", "Engine Platform Admin");
        grantPlatformAdmin(platformAdmin.userId());

        mockMvc.perform(post("/api/v1/engine/external/review-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewPurpose\":\"preflight\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ENGINE_API_KEY_INVALID"));

        var createBody = """
                {
                  "displayName": "Codex test key",
                  "ownerUserId": %d,
                  "officeId": %d,
                  "scopes": ["ENGINE_REVIEW_SESSION"]
                }
                """.formatted(owner.userId(), owner.officeId());
        var createResult = mockMvc.perform(post("/api/v1/platform-admin/engine/api-keys")
                        .header("Authorization", bearer(platformAdmin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key.maskedKey").value(org.hamcrest.Matchers.startsWith("adx_live_")))
                .andExpect(jsonPath("$.key.status").value("ACTIVE"))
                .andReturn();

        var created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        var apiKey = created.get("apiKey").asText();
        var apiKeyId = created.get("key").get("id").asLong();
        org.assertj.core.api.Assertions.assertThat(apiKey).startsWith("adx_live_");
        seedLegalBindingForRebar();

        var sessionResult = mockMvc.perform(post("/api/v1/engine/external/review-sessions")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerProjectRef\":\"outside-project\",\"reviewPurpose\":\"preflight\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewSessionId").value(org.hamcrest.Matchers.startsWith("rvw_sess_")))
                .andExpect(jsonPath("$.customerProjectRef").value("outside-project"))
                .andReturn();
        var reviewSessionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .get("reviewSessionId")
                .asText();

        mockMvc.perform(get("/api/v1/engine/external/review-sessions/{reviewSessionId}", reviewSessionId)
                        .header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewSessionId").value(reviewSessionId));

        mockMvc.perform(post("/api/v1/engine/external/review-sessions/{reviewSessionId}/facts", reviewSessionId)
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "facts": [
                                    {"name": "buildingUse", "rawValue": "neighborhood living facility", "source": "CUSTOMER_AGENT_EXTRACTED", "confidence": 0.91},
                                    {"name": "structureType", "rawValue": "RC", "source": "CUSTOMER_SYSTEM", "confidence": 0.98},
                                    {"name": "workType", "rawValue": "foundation concrete placement", "source": "USER_PROVIDED", "confidence": 0.88},
                                    {"name": "tradeCode", "rawValue": "REINFORCED_CONCRETE", "source": "CUSTOMER_SYSTEM", "confidence": 0.95},
                                    {"name": "processCode", "rawValue": "REBAR_ASSEMBLY", "source": "CUSTOMER_SYSTEM", "confidence": 0.95},
                                    {"name": "inspectionItemCode", "rawValue": "REBAR_SPACING", "source": "CUSTOMER_SYSTEM", "confidence": 0.95}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/engine/external/review-sessions/{reviewSessionId}/run-validation", reviewSessionId)
                        .header("X-ArchDox-Engine-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationResult.status").value("FAIL"))
                .andExpect(jsonPath("$.validationResult.findings[*].code")
                        .value(org.hamcrest.Matchers.hasItem("CATALOG_SELECTION_INVALID")))
                .andExpect(jsonPath("$.validationResult.nextActions[0].actionType")
                        .value("DATA_FIX"))
                .andExpect(jsonPath("$.validationResult.nextActions[0].blocking")
                        .value(true))
                .andExpect(jsonPath("$.validationResult.nextActions[*].code")
                        .value(org.hamcrest.Matchers.hasItem("FIX_CATALOG_SELECTION")));

        var validSessionResult = mockMvc.perform(post("/api/v1/engine/external/review-sessions")
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerProjectRef\":\"outside-project-legal\",\"reviewPurpose\":\"preflight\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        var validReviewSessionId = objectMapper.readTree(validSessionResult.getResponse().getContentAsString())
                .get("reviewSessionId")
                .asText();

        mockMvc.perform(post("/api/v1/engine/external/review-sessions/{reviewSessionId}/facts", validReviewSessionId)
                        .header("X-ArchDox-Engine-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "facts": [
                                    {"name": "buildingUse", "rawValue": "neighborhood living facility", "source": "CUSTOMER_AGENT_EXTRACTED", "confidence": 0.91},
                                    {"name": "structureType", "rawValue": "RC", "source": "CUSTOMER_SYSTEM", "confidence": 0.98},
                                    {"name": "workType", "rawValue": "foundation concrete placement", "source": "USER_PROVIDED", "confidence": 0.88},
                                    {"name": "tradeCode", "rawValue": "REINFORCED_CONCRETE", "source": "CUSTOMER_SYSTEM", "confidence": 0.95},
                                    {"name": "processCode", "rawValue": "REBAR_ASSEMBLY", "source": "CUSTOMER_SYSTEM", "confidence": 0.95},
                                    {"name": "inspectionItemCode", "rawValue": "RC_REBAR_COUNT_DIAMETER_PITCH", "source": "CUSTOMER_SYSTEM", "confidence": 0.95}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/engine/external/review-sessions/{reviewSessionId}/run-validation", validReviewSessionId)
                        .header("X-ArchDox-Engine-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationResult.status").value("WARN"))
                .andExpect(jsonPath("$.validationResult.metadata.catalogBindings[0].inspectionItemCode")
                        .value("RC_REBAR_COUNT_DIAMETER_PITCH"))
                .andExpect(jsonPath("$.validationResult.legalReferences[0].actCode")
                        .value("ENGINE_TEST_BUILDING_STANDARD"))
                .andExpect(jsonPath("$.validationResult.legalReferences[0].referenceId")
                        .value(org.hamcrest.Matchers.containsString("ENGINE_TEST_BUILDING_STANDARD")))
                .andExpect(jsonPath("$.validationResult.legalReferences[0].articleNo")
                        .value("2"))
                .andExpect(jsonPath("$.validationResult.metadata.legalReferences[0].actCode")
                        .value("ENGINE_TEST_BUILDING_STANDARD"))
                .andExpect(jsonPath("$.validationResult.findings[*].code")
                        .value(org.hamcrest.Matchers.hasItem("LEGAL_EVIDENCE_CONTEXT_MISSING")))
                .andExpect(jsonPath("$.validationResult.nextActions[*].code")
                        .value(org.hamcrest.Matchers.hasItem("ADD_SUPERVISION_EVIDENCE_CONTEXT")))
                .andExpect(jsonPath("$.validationResult.nextActions[*].actionType")
                        .value(org.hamcrest.Matchers.hasItem("USER_INPUT")))
                .andExpect(jsonPath("$.validationResult.metadata.legalRiskReview.aiPromptContext.purpose")
                        .value("SOURCE_BACKED_LEGAL_RISK_REVIEW_CONTEXT"));

        mockMvc.perform(get("/api/v1/platform-admin/engine/usage/events")
                        .header("Authorization", bearer(platformAdmin.accessToken()))
                        .param("operation", "RUN_VALIDATION")
                        .param("reviewSessionId", validReviewSessionId)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].capability").value("ENGINE_REVIEW_SESSION"))
                .andExpect(jsonPath("$[0].metadata.engineStatus").value("WARN"))
                .andExpect(jsonPath("$[0].metadata.findingCount").value(1))
                .andExpect(jsonPath("$[0].metadata.legalReferenceCount").value(1))
                .andExpect(jsonPath("$[0].metadata.legalReferenceIds[0]")
                        .value(org.hamcrest.Matchers.containsString("ENGINE_TEST_BUILDING_STANDARD")))
                .andExpect(jsonPath("$[0].metadata.legalReferenceSources[0]").value("LEGAL_DOMAIN_BINDING"))
                .andExpect(jsonPath("$[0].metadata.findingCodes[0]").value("LEGAL_EVIDENCE_CONTEXT_MISSING"));

        var noOfficeCreateBody = """
                {
                  "displayName": "No office key",
                  "ownerUserId": %d,
                  "scopes": ["ENGINE_REVIEW_SESSION"]
                }
                """.formatted(owner.userId());
        var noOfficeCreateResult = mockMvc.perform(post("/api/v1/platform-admin/engine/api-keys")
                        .header("Authorization", bearer(platformAdmin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noOfficeCreateBody))
                .andExpect(status().isCreated())
                .andReturn();
        var noOfficeApiKey = objectMapper.readTree(noOfficeCreateResult.getResponse().getContentAsString())
                .get("apiKey")
                .asText();

        mockMvc.perform(get("/api/v1/engine/external/review-sessions/{reviewSessionId}", reviewSessionId)
                        .header("X-ArchDox-Engine-Key", noOfficeApiKey))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/platform-admin/engine/api-keys/{apiKeyId}/revoke", apiKeyId)
                        .header("Authorization", bearer(platformAdmin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        mockMvc.perform(get("/api/v1/engine/external/review-sessions/{reviewSessionId}", reviewSessionId)
                        .header("X-ArchDox-Engine-Key", apiKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ENGINE_API_KEY_INVALID"));
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

    private void grantPlatformAdmin(long userId) {
        var now = OffsetDateTime.now();
        jdbcTemplate.update("""
                        insert into platform_admins (user_id, role, status, created_at, updated_at)
                        values (?, 'SUPER_ADMIN', 'ACTIVE', ?, ?)
                        """,
                userId,
                now,
                now);
    }

    private void seedLegalBindingForRebar() {
        var now = OffsetDateTime.now();
        var sourceId = jdbcTemplate.queryForObject("""
                        insert into legal_sources
                            (code, source_type, display_name, base_url, status, metadata_json, created_at, updated_at)
                        values
                            ('ENGINE_TEST_LEGAL_SOURCE', 'FAKE', 'Engine Test Legal Source', 'https://example.test', 'ACTIVE', '{}'::jsonb, ?, ?)
                        returning id
                        """,
                Long.class,
                now,
                now);
        var actId = jdbcTemplate.queryForObject("""
                        insert into legal_acts
                            (source_id, act_code, act_name, act_type, jurisdiction, source_law_id, status, created_at, updated_at)
                        values
                            (?, 'ENGINE_TEST_BUILDING_STANDARD', 'Engine Test Building Standard', 'ADMIN_RULE', 'KR', 'ENGINE-TEST-001', 'ACTIVE', ?, ?)
                        returning id
                        """,
                Long.class,
                sourceId,
                now,
                now);
        var versionId = jdbcTemplate.queryForObject("""
                        insert into legal_versions
                            (act_id, source_version_key, promulgation_date, effective_date, source_url, content_hash, source_metadata_json, captured_at)
                        values
                            (?, 'engine-test-v1', '2026-06-04', '2026-06-04', 'https://example.test/legal/engine-test-v1', 'engine-test-hash', '{}'::jsonb, ?)
                        returning id
                        """,
                Long.class,
                actId,
                now);
        var articleId = jdbcTemplate.queryForObject("""
                        insert into legal_articles
                            (act_id, article_key, article_no, article_title, parent_article_key, sort_order, created_at, updated_at)
                        values
                            (?, 'ARTICLE_002', '2', 'Supervision Evidence', null, 20, ?, ?)
                        returning id
                        """,
                Long.class,
                actId,
                now,
                now);
        jdbcTemplate.update("""
                        insert into legal_article_versions
                            (article_id, legal_version_id, article_key, article_no, article_title, article_text, normalized_text,
                             content_hash, effective_date, source_metadata_json, created_at)
                        values
                            (?, ?, 'ARTICLE_002', '2', 'Supervision Evidence',
                             'Supervision records should include date, responsible person, work area, and evidence where relevant.',
                             'supervision records should include date responsible person work area and evidence where relevant',
                             'engine-test-article-hash', '2026-06-04', '{}'::jsonb, ?)
                        """,
                articleId,
                versionId,
                now);
        jdbcTemplate.update("""
                        insert into legal_domain_bindings
                            (binding_scope, binding_key, act_id, article_id, report_type, catalog_code, catalog_version,
                             checklist_item_code, relevance, status, effective_from, effective_to, notes, metadata_json,
                             created_at, updated_at)
                        values
                            ('CATALOG_ITEM',
                             'CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24:v2:RC_REBAR_COUNT_DIAMETER_PITCH',
                             ?, ?, null, 'CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24', 2,
                             'RC_REBAR_COUNT_DIAMETER_PITCH', 'DIRECT', 'ACTIVE', null, null,
                             'Engine test binding for reinforced concrete rebar evidence.', '{}'::jsonb, ?, ?)
                        """,
                actId,
                articleId,
                now,
                now);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(long userId, long officeId, String accessToken) {
    }
}

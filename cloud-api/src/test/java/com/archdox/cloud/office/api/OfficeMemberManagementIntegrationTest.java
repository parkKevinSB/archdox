package com.archdox.cloud.office.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class OfficeMemberManagementIntegrationTest {
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
    void ownerCanManageOfficeMembersAndEventsAreRecorded() throws Exception {
        var owner = signup("member-owner@example.com", "Member Owner");
        var member = signup("member-target@example.com", "Member Target");
        var officeId = createOffice(owner, "Member Owner Office");

        addMember(owner, officeId, member.email(), "MEMBER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(member.userId()))
                .andExpect(jsonPath("$.officeId").value(officeId))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/offices/{officeId}/members", officeId)
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == %d && @.role == 'MEMBER')]".formatted(member.userId())).exists());

        mockMvc.perform(get("/api/v1/offices/{officeId}/members", officeId)
                        .header("Authorization", bearer(member.accessToken()))
                        .header("X-Office-Id", officeId))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/offices/{officeId}/members/{memberUserId}/role", officeId, member.userId())
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(delete("/api/v1/offices/{officeId}/members/{memberUserId}", officeId, member.userId())
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        addMember(owner, officeId, member.email(), "VIEWER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        var events = mockMvc.perform(get("/api/v1/operation-events")
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId)
                        .param("resourceType", "OFFICE_MEMBER")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(events.contains("OFFICE_MEMBER_ADDED"));
        assertTrue(events.contains("OFFICE_MEMBER_ROLE_CHANGED"));
        assertTrue(events.contains("OFFICE_MEMBER_DEACTIVATED"));
        assertTrue(events.contains("OFFICE_MEMBER_REACTIVATED"));
    }

    @Test
    void memberManagementProtectsOwnerRole() throws Exception {
        var owner = signup("owner-protection@example.com", "Owner Protection");
        var admin = signup("admin-protection@example.com", "Admin Protection");
        var secondOwner = signup("second-owner@example.com", "Second Owner");
        var officeId = createOffice(owner, "Owner Protection Office");

        addMember(owner, officeId, admin.email(), "ADMIN")
                .andExpect(status().isCreated());

        addMember(admin, officeId, secondOwner.email(), "OWNER")
                .andExpect(status().isForbidden());

        addMember(owner, officeId, secondOwner.email(), "OWNER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OWNER"));

        mockMvc.perform(patch("/api/v1/offices/{officeId}/members/{memberUserId}/role", officeId, owner.userId())
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/offices/{officeId}/members/{memberUserId}", officeId, owner.userId())
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/offices/{officeId}/members/{memberUserId}/role", officeId, secondOwner.userId())
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    private org.springframework.test.web.servlet.ResultActions addMember(
            TestUser actor,
            long officeId,
            String email,
            String role
    ) throws Exception {
        var body = """
                {
                  "email": "%s",
                  "role": "%s"
                }
                """.formatted(email, role);
        return mockMvc.perform(post("/api/v1/offices/{officeId}/members", officeId)
                .header("Authorization", bearer(actor.accessToken()))
                .header("X-Office-Id", officeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long createOffice(TestUser owner, String displayName) throws Exception {
        var officeResult = mockMvc.perform(post("/api/v1/offices")
                        .header("Authorization", bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "%s"
                                }
                                """.formatted(displayName)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(officeResult.getResponse().getContentAsString())
                .get("id")
                .asLong();
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

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(long userId, String email, long officeId, String accessToken) {
    }
}

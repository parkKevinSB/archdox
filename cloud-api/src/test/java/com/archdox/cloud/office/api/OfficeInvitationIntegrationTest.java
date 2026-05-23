package com.archdox.cloud.office.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class OfficeInvitationIntegrationTest {
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
    void ownerCreatesInvitationAndInviteeAcceptsIt() throws Exception {
        var owner = signup("invite-owner@example.com", "Invite Owner");
        var invitee = signup("invite-target@example.com", "Invite Target");
        var officeId = createOffice(owner, "Invite Owner Office");

        var createResult = mockMvc.perform(post("/api/v1/offices/{officeId}/invitations", officeId)
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invite-target@example.com",
                                  "role": "MEMBER",
                                  "expiresInDays": 7
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("invite-target@example.com"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.acceptToken").isString())
                .andExpect(jsonPath("$.acceptPath").isString())
                .andReturn();
        var token = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("acceptToken")
                .asText();

        mockMvc.perform(get("/api/v1/offices/{officeId}/invitations", officeId)
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].acceptToken").doesNotExist())
                .andExpect(jsonPath("$[0].tokenPreview").isString());

        mockMvc.perform(post("/api/v1/office-invitations/{token}/accept", token)
                        .header("Authorization", bearer(invitee.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(invitee.userId()))
                .andExpect(jsonPath("$.officeId").value(officeId))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        var events = mockMvc.perform(get("/api/v1/operation-events")
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId)
                        .param("resourceType", "OFFICE_INVITATION")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(events.contains("OFFICE_INVITATION_CREATED"));
        assertTrue(events.contains("OFFICE_INVITATION_ACCEPTED"));
    }

    @Test
    void officeSignupRequiresMatchingOfficeCodeAndInvitation() throws Exception {
        var owner = signup("office-signup-owner@example.com", "Office Signup Owner");
        var officeResult = mockMvc.perform(post("/api/v1/offices")
                        .header("Authorization", bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Signup Architecture Office"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        var office = objectMapper.readTree(officeResult.getResponse().getContentAsString());
        var officeId = office.get("id").asLong();
        var officeCode = office.get("officeCode").asText();

        var invitationResult = mockMvc.perform(post("/api/v1/offices/{officeId}/invitations", officeId)
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "office-signup-target@example.com",
                                  "role": "MEMBER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        var invitationToken = objectMapper.readTree(invitationResult.getResponse().getContentAsString())
                .get("acceptToken")
                .asText();

        mockMvc.perform(get("/api/v1/office-invitations/{token}", invitationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("office-signup-target@example.com"))
                .andExpect(jsonPath("$.officeId").value(officeId))
                .andExpect(jsonPath("$.officeCode").value(officeCode))
                .andExpect(jsonPath("$.officeDisplayName").value("Signup Architecture Office"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        var signupResult = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "office-signup-target@example.com",
                                  "password": "password-1234",
                                  "name": "Office Signup Target",
                                  "accountType": "OFFICE",
                                  "officeCode": "%s",
                                  "invitationToken": "%s"
                                }
                                """.formatted(officeCode, invitationToken)))
                .andExpect(status().isCreated())
                .andReturn();
        var accessToken = objectMapper.readTree(signupResult.getResponse().getContentAsString())
                .get("accessToken")
                .asText();

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offices.length()").value(1))
                .andExpect(jsonPath("$.offices[0].id").value(officeId))
                .andExpect(jsonPath("$.offices[0].type").value("OFFICE"))
                .andExpect(jsonPath("$.offices[0].role").value("MEMBER"));
    }

    @Test
    void invitationProtectsEmailOwnerRoleAndCancellation() throws Exception {
        var owner = signup("invite-protect-owner@example.com", "Invite Protect Owner");
        var admin = signup("invite-protect-admin@example.com", "Invite Protect Admin");
        var invitee = signup("invite-protect-target@example.com", "Invite Protect Target");
        var stranger = signup("invite-protect-stranger@example.com", "Invite Protect Stranger");
        var officeId = createOffice(owner, "Invite Protect Office");

        addMember(owner, officeId, admin.email(), "ADMIN")
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/offices/{officeId}/invitations", officeId)
                        .header("Authorization", bearer(admin.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "owner-invitee@example.com",
                                  "role": "OWNER"
                                }
                                """))
                .andExpect(status().isForbidden());

        var createResult = mockMvc.perform(post("/api/v1/offices/{officeId}/invitations", officeId)
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invite-protect-target@example.com",
                                  "role": "VIEWER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        var invitation = objectMapper.readTree(createResult.getResponse().getContentAsString());
        var invitationId = invitation.get("id").asLong();
        var token = invitation.get("acceptToken").asText();

        mockMvc.perform(post("/api/v1/office-invitations/{token}/accept", token)
                        .header("Authorization", bearer(stranger.accessToken())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/offices/{officeId}/invitations/{invitationId}", officeId, invitationId)
                        .header("Authorization", bearer(owner.accessToken()))
                        .header("X-Office-Id", officeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/office-invitations/{token}/accept", token)
                        .header("Authorization", bearer(invitee.accessToken())))
                .andExpect(status().isConflict());
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

package com.archdox.cloud.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentAuthMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentDeploymentMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentInstallToken;
import com.archdox.cloud.agent.domain.ArchDoxAgentInstallTokenStatus;
import com.archdox.cloud.agent.domain.ArchDoxAgentStatus;
import com.archdox.cloud.agent.dto.CreateArchDoxAgentInstallTokenRequest;
import com.archdox.cloud.agent.infra.ArchDoxAgentInstallTokenRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentRepository;
import com.archdox.cloud.global.api.UnauthorizedException;
import com.archdox.cloud.office.domain.OfficeMembership;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.shared.MembershipRole;
import com.archdox.shared.MembershipStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ArchDoxAgentAuthenticationServiceTest {
    private final ArchDoxAgentRepository agentRepository = mock(ArchDoxAgentRepository.class);
    private final ArchDoxAgentInstallTokenRepository installTokenRepository = mock(ArchDoxAgentInstallTokenRepository.class);
    private final OfficeRepository officeRepository = mock(OfficeRepository.class);
    private final OfficeMembershipRepository membershipRepository = mock(OfficeMembershipRepository.class);
    private final ArchDoxAgentProperties properties = new ArchDoxAgentProperties();
    private final ArchDoxAgentSecretHasher secretHasher = new ArchDoxAgentSecretHasher();
    private final ArchDoxAgentRuntimeCompatibilityService runtimeCompatibilityService =
            new ArchDoxAgentRuntimeCompatibilityService(properties);
    private final ArchDoxAgentAuthenticationService service = new ArchDoxAgentAuthenticationService(
            agentRepository,
            installTokenRepository,
            officeRepository,
            membershipRepository,
            properties,
            secretHasher,
            runtimeCompatibilityService);

    @Test
    void issueInstallTokenStoresHashOnly() {
        var membership = mock(OfficeMembership.class);
        when(membership.role()).thenReturn(MembershipRole.ADMIN);
        when(membershipRepository.findByUserIdAndOfficeIdAndStatus(1L, 10L, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership));
        when(agentRepository.findByOfficeIdAndAgentCode(10L, "office-main")).thenReturn(Optional.empty());
        when(agentRepository.save(any(ArchDoxAgent.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 7L));
        when(installTokenRepository.save(any(ArchDoxAgentInstallToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var issued = service.issueInstallToken(10L, 1L, new CreateArchDoxAgentInstallTokenRequest(5, null, null));

        assertNotNull(issued.rawToken());
        assertNotEquals(issued.rawToken(), issued.installToken().tokenHash());
        assertEquals(10L, issued.installToken().officeId());
        assertEquals(7L, issued.installToken().agentId());
        assertEquals("office-main", issued.agent().agentCode());
        assertEquals(ArchDoxAgentStatus.PENDING_PAIR, issued.agent().status());
        assertEquals(ArchDoxAgentInstallTokenStatus.ACTIVE, issued.installToken().status());
    }

    @Test
    void installTokenHelloPairsAgentAndIssuesDeviceSecret() {
        var rawInstallToken = "install-token";
        var installToken = new ArchDoxAgentInstallToken(
                10L,
                7L,
                secretHasher.hash(rawInstallToken),
                OffsetDateTime.now().plusMinutes(10),
                1L,
                OffsetDateTime.now());
        var agent = new ArchDoxAgent(10L, "agent-a", "0.0.1", Map.of(), OffsetDateTime.now());
        ReflectionTestUtils.setField(agent, "id", 7L);
        when(officeRepository.existsById(10L)).thenReturn(true);
        when(installTokenRepository.findByTokenHash(secretHasher.hash(rawInstallToken)))
                .thenReturn(Optional.of(installToken));
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));

        var connection = service.connect(new AgentHello(
                "INSTALL_TOKEN",
                null,
                10L,
                "agent-a",
                null,
                rawInstallToken,
                null,
                "0.0.1",
                Map.of("photoPickup", true)));

        assertEquals(ArchDoxAgentInstallTokenStatus.USED, installToken.status());
        assertEquals(ArchDoxAgentAuthMode.DEVICE_SECRET, connection.agent().authMode());
        assertNotNull(connection.agent().deviceSecretHash());
        assertNotNull(connection.issuedDeviceSecret());
    }

    @Test
    void installTokenHelloRejectsUnregisteredAgentCode() {
        var rawInstallToken = "install-token";
        var installToken = new ArchDoxAgentInstallToken(
                10L,
                7L,
                secretHasher.hash(rawInstallToken),
                OffsetDateTime.now().plusMinutes(10),
                1L,
                OffsetDateTime.now());
        var agent = new ArchDoxAgent(10L, "registered-agent", "0.0.1", Map.of(), OffsetDateTime.now());
        ReflectionTestUtils.setField(agent, "id", 7L);
        when(officeRepository.existsById(10L)).thenReturn(true);
        when(installTokenRepository.findByTokenHash(secretHasher.hash(rawInstallToken)))
                .thenReturn(Optional.of(installToken));
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));

        assertThrows(UnauthorizedException.class, () -> service.connect(new AgentHello(
                "INSTALL_TOKEN",
                null,
                10L,
                "other-agent",
                null,
                rawInstallToken,
                null,
                "0.0.1",
                Map.of("photoPickup", true))));
    }

    @Test
    void deviceSecretHelloRejectsWrongSecret() {
        var agent = new ArchDoxAgent(10L, "agent-a", "0.0.1", Map.of(), OffsetDateTime.now());
        agent.pairDeviceSecret(secretHasher.hash("correct"), "0.0.1", Map.of(), OffsetDateTime.now());
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));

        assertThrows(UnauthorizedException.class, () -> service.connect(new AgentHello(
                "DEVICE_SECRET",
                7L,
                null,
                null,
                null,
                null,
                "wrong",
                "0.0.1",
                Map.of())));
    }

    @Test
    void deviceSecretHelloRejectsDeploymentModeChange() {
        var agent = new ArchDoxAgent(
                10L,
                "agent-a",
                ArchDoxAgentDeploymentMode.LOCAL_OFFICE,
                "0.0.1",
                Map.of(),
                Map.of(),
                OffsetDateTime.now());
        agent.pairDeviceSecret(secretHasher.hash("correct"), ArchDoxAgentDeploymentMode.LOCAL_OFFICE, "0.0.1", Map.of(), Map.of(), OffsetDateTime.now());
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));

        assertThrows(UnauthorizedException.class, () -> service.connect(new AgentHello(
                "DEVICE_SECRET",
                7L,
                null,
                null,
                null,
                null,
                "correct",
                "0.0.1",
                "2026-06-25",
                "embedded",
                "stable",
                "CLOUD_MANAGED",
                Map.of(),
                Map.of())));
    }

    @Test
    void downloadAuthRequiresAgentOfficeMatch() {
        var agent = new ArchDoxAgent(10L, "agent-a", "0.0.1", Map.of(), OffsetDateTime.now());
        agent.pairDeviceSecret(secretHasher.hash("device-secret"), "0.0.1", Map.of(), OffsetDateTime.now());
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));

        assertThrows(UnauthorizedException.class,
                () -> service.authenticateDownload(7L, "device-secret", null, 11L));
        verify(agentRepository).findById(7L);
    }

    private ArchDoxAgent withId(ArchDoxAgent agent, Long id) {
        ReflectionTestUtils.setField(agent, "id", id);
        return agent;
    }
}

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
import com.archdox.cloud.agent.domain.ArchDoxAgentInstallToken;
import com.archdox.cloud.agent.domain.ArchDoxAgentInstallTokenStatus;
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

class ArchDoxAgentAuthenticationServiceTest {
    private final ArchDoxAgentRepository agentRepository = mock(ArchDoxAgentRepository.class);
    private final ArchDoxAgentInstallTokenRepository installTokenRepository = mock(ArchDoxAgentInstallTokenRepository.class);
    private final OfficeRepository officeRepository = mock(OfficeRepository.class);
    private final OfficeMembershipRepository membershipRepository = mock(OfficeMembershipRepository.class);
    private final ArchDoxAgentProperties properties = new ArchDoxAgentProperties();
    private final ArchDoxAgentSecretHasher secretHasher = new ArchDoxAgentSecretHasher();
    private final ArchDoxAgentAuthenticationService service = new ArchDoxAgentAuthenticationService(
            agentRepository,
            installTokenRepository,
            officeRepository,
            membershipRepository,
            properties,
            secretHasher);

    @Test
    void issueInstallTokenStoresHashOnly() {
        var membership = mock(OfficeMembership.class);
        when(membership.role()).thenReturn(MembershipRole.ADMIN);
        when(membershipRepository.findByUserIdAndOfficeIdAndStatus(1L, 10L, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership));
        when(installTokenRepository.save(any(ArchDoxAgentInstallToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var issued = service.issueInstallToken(10L, 1L, 5);

        assertNotNull(issued.rawToken());
        assertNotEquals(issued.rawToken(), issued.installToken().tokenHash());
        assertEquals(10L, issued.installToken().officeId());
        assertEquals(ArchDoxAgentInstallTokenStatus.ACTIVE, issued.installToken().status());
    }

    @Test
    void installTokenHelloPairsAgentAndIssuesDeviceSecret() {
        var rawInstallToken = "install-token";
        var installToken = new ArchDoxAgentInstallToken(
                10L,
                secretHasher.hash(rawInstallToken),
                OffsetDateTime.now().plusMinutes(10),
                1L,
                OffsetDateTime.now());
        when(officeRepository.existsById(10L)).thenReturn(true);
        when(installTokenRepository.findByTokenHash(secretHasher.hash(rawInstallToken)))
                .thenReturn(Optional.of(installToken));
        when(agentRepository.findByOfficeIdAndAgentCode(10L, "agent-a")).thenReturn(Optional.empty());
        when(agentRepository.save(any(ArchDoxAgent.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
    void downloadAuthRequiresAgentOfficeMatch() {
        var agent = new ArchDoxAgent(10L, "agent-a", "0.0.1", Map.of(), OffsetDateTime.now());
        agent.pairDeviceSecret(secretHasher.hash("device-secret"), "0.0.1", Map.of(), OffsetDateTime.now());
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));

        assertThrows(UnauthorizedException.class,
                () -> service.authenticateDownload(7L, "device-secret", null, 11L));
        verify(agentRepository).findById(7L);
    }
}

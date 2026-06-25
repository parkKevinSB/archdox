package com.archdox.cloud.platformadmin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.agent.application.ArchDoxAgentSecretHasher;
import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentAuthMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentDeploymentMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.platformadmin.domain.PlatformAdmin;
import com.archdox.cloud.platformadmin.domain.PlatformAdminRole;
import com.archdox.cloud.platformadmin.dto.ProvisionCloudManagedAgentRequest;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PlatformCloudManagedAgentProvisioningServiceTest {
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final OfficeRepository officeRepository = mock(OfficeRepository.class);
    private final ArchDoxAgentRepository agentRepository = mock(ArchDoxAgentRepository.class);
    private final ArchDoxAgentSecretHasher secretHasher = new ArchDoxAgentSecretHasher();
    private final PlatformCloudManagedAgentProvisioningService service = new PlatformCloudManagedAgentProvisioningService(
            platformAdminService,
            officeRepository,
            agentRepository,
            secretHasher);

    @Test
    void provisionsCloudManagedDeviceSecretWithoutMarkingAgentOnline() {
        var principal = new UserPrincipal(3L, "admin@example.com");
        when(platformAdminService.requirePlatformAdmin(principal))
                .thenReturn(new PlatformAdmin(3L, PlatformAdminRole.SUPER_ADMIN, OffsetDateTime.now()));
        when(officeRepository.existsById(1L)).thenReturn(true);
        when(agentRepository.findByOfficeIdAndAgentCode(1L, "cloud-personal-main")).thenReturn(Optional.empty());
        when(agentRepository.save(any(ArchDoxAgent.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 11L));

        var response = service.provision(
                principal,
                new ProvisionCloudManagedAgentRequest(
                        1L,
                        "cloud-personal-main",
                        Map.of("kind", "S3_COMPATIBLE", "secretKey", "must-not-be-stored")));

        assertEquals(1L, response.officeId());
        assertEquals(11L, response.agentId());
        assertEquals("cloud-personal-main", response.agentCode());
        assertEquals(ArchDoxAgentDeploymentMode.CLOUD_MANAGED, response.deploymentMode());
        assertEquals(ArchDoxAgentAuthMode.DEVICE_SECRET, response.authMode());
        assertEquals(ArchDoxAgentStatus.OFFLINE, response.status());
        assertNotNull(response.deviceSecret());
        assertNotNull(response.pairedAt());
        assertEquals(false, response.storageProfile().containsKey("secretKey"));
    }

    @Test
    void rejectsExistingNonCloudManagedAgentCode() {
        var principal = new UserPrincipal(3L, "admin@example.com");
        var existing = new ArchDoxAgent(
                1L,
                "office-main",
                ArchDoxAgentDeploymentMode.LOCAL_OFFICE,
                "0.0.1",
                Map.of(),
                Map.of(),
                OffsetDateTime.now());
        when(platformAdminService.requirePlatformAdmin(principal))
                .thenReturn(new PlatformAdmin(3L, PlatformAdminRole.SUPER_ADMIN, OffsetDateTime.now()));
        when(officeRepository.existsById(1L)).thenReturn(true);
        when(agentRepository.findByOfficeIdAndAgentCode(1L, "office-main")).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class, () -> service.provision(
                principal,
                new ProvisionCloudManagedAgentRequest(1L, "office-main", Map.of())));
    }

    private ArchDoxAgent withId(ArchDoxAgent agent, Long id) {
        ReflectionTestUtils.setField(agent, "id", id);
        return agent;
    }
}

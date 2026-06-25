package com.archdox.cloud.platformadmin.application;

import com.archdox.cloud.agent.application.ArchDoxAgentSecretHasher;
import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentDeploymentMode;
import com.archdox.cloud.agent.infra.ArchDoxAgentRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.platformadmin.dto.ProvisionCloudManagedAgentRequest;
import com.archdox.cloud.platformadmin.dto.ProvisionCloudManagedAgentResponse;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformCloudManagedAgentProvisioningService {
    private final PlatformAdminService platformAdminService;
    private final OfficeRepository officeRepository;
    private final ArchDoxAgentRepository agentRepository;
    private final ArchDoxAgentSecretHasher secretHasher;

    public PlatformCloudManagedAgentProvisioningService(
            PlatformAdminService platformAdminService,
            OfficeRepository officeRepository,
            ArchDoxAgentRepository agentRepository,
            ArchDoxAgentSecretHasher secretHasher
    ) {
        this.platformAdminService = platformAdminService;
        this.officeRepository = officeRepository;
        this.agentRepository = agentRepository;
        this.secretHasher = secretHasher;
    }

    @Transactional
    public ProvisionCloudManagedAgentResponse provision(
            UserPrincipal principal,
            ProvisionCloudManagedAgentRequest request
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        if (request == null || request.officeId() == null) {
            throw new BadRequestException("officeId is required");
        }
        if (!officeRepository.existsById(request.officeId())) {
            throw new NotFoundException("Office not found");
        }

        var now = OffsetDateTime.now();
        var agentCode = normalizedAgentCode(request.agentCode());
        var storageProfile = storageProfile(request.storageProfile());
        var agent = agentRepository.findByOfficeIdAndAgentCode(request.officeId(), agentCode)
                .orElseGet(() -> agentRepository.save(ArchDoxAgent.registerPendingPair(
                        request.officeId(),
                        agentCode,
                        ArchDoxAgentDeploymentMode.CLOUD_MANAGED,
                        now)));
        if (agent.deploymentMode() != ArchDoxAgentDeploymentMode.CLOUD_MANAGED) {
            throw new BadRequestException("Registered Agent deployment mode is not CLOUD_MANAGED");
        }

        var rawDeviceSecret = secretHasher.generateSecret();
        agent.provisionDeviceSecret(
                secretHasher.hash(rawDeviceSecret),
                ArchDoxAgentDeploymentMode.CLOUD_MANAGED,
                storageProfile,
                now);

        return new ProvisionCloudManagedAgentResponse(
                agent.officeId(),
                agent.id(),
                agent.agentCode(),
                agent.deploymentMode(),
                agent.authMode(),
                agent.status(),
                rawDeviceSecret,
                agent.pairedAt(),
                agent.storageProfileJson());
    }

    private String normalizedAgentCode(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            return "cloud-managed-1";
        }
        return agentCode.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> storageProfile(Map<String, Object> requested) {
        var profile = new LinkedHashMap<String, Object>();
        profile.put("kind", "S3_COMPATIBLE");
        profile.put("managedBy", "ARCHDOX_CLOUD");
        if (requested != null) {
            requested.forEach((key, value) -> {
                if (key != null && value != null && !String.valueOf(key).isBlank()) {
                    profile.put(String.valueOf(key), value);
                }
            });
        }
        profile.remove("rootPath");
        profile.remove("accessKey");
        profile.remove("secretKey");
        profile.remove("deviceSecret");
        return Map.copyOf(profile);
    }
}

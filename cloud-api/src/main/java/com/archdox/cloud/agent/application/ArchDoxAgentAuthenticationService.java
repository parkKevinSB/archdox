package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentAuthMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentDeploymentMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentInstallToken;
import com.archdox.cloud.agent.domain.ArchDoxAgentInstallTokenStatus;
import com.archdox.cloud.agent.dto.CreateArchDoxAgentInstallTokenRequest;
import com.archdox.cloud.agent.infra.ArchDoxAgentInstallTokenRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.api.UnauthorizedException;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.shared.MembershipRole;
import com.archdox.shared.MembershipStatus;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchDoxAgentAuthenticationService {
    private final ArchDoxAgentRepository agentRepository;
    private final ArchDoxAgentInstallTokenRepository installTokenRepository;
    private final OfficeRepository officeRepository;
    private final OfficeMembershipRepository membershipRepository;
    private final ArchDoxAgentProperties properties;
    private final ArchDoxAgentSecretHasher secretHasher;
    private final ArchDoxAgentRuntimeCompatibilityService compatibilityService;

    public ArchDoxAgentAuthenticationService(
            ArchDoxAgentRepository agentRepository,
            ArchDoxAgentInstallTokenRepository installTokenRepository,
            OfficeRepository officeRepository,
            OfficeMembershipRepository membershipRepository,
            ArchDoxAgentProperties properties,
            ArchDoxAgentSecretHasher secretHasher,
            ArchDoxAgentRuntimeCompatibilityService compatibilityService
    ) {
        this.agentRepository = agentRepository;
        this.installTokenRepository = installTokenRepository;
        this.officeRepository = officeRepository;
        this.membershipRepository = membershipRepository;
        this.properties = properties;
        this.secretHasher = secretHasher;
        this.compatibilityService = compatibilityService;
    }

    @Transactional
    public IssuedArchDoxAgentInstallToken issueInstallToken(
            Long officeId,
            Long userId,
            CreateArchDoxAgentInstallTokenRequest request
    ) {
        requireOfficeAdmin(userId, officeId);
        var now = OffsetDateTime.now();
        var ttlMinutes = request == null || request.expiresInMinutes() == null
                ? properties.safeInstallTokenTtlMinutes()
                : Math.max(1, request.expiresInMinutes());
        var agentCode = normalizedAgentCode(request == null ? null : request.agentCode());
        var deploymentMode = deploymentMode(request == null ? null : request.deploymentMode());
        var agent = agentRepository.findByOfficeIdAndAgentCode(officeId, agentCode)
                .orElseGet(() -> agentRepository.save(ArchDoxAgent.registerPendingPair(
                        officeId,
                        agentCode,
                        deploymentMode,
                        now)));
        if (agent.deploymentMode() != deploymentMode) {
            throw new BadRequestException("Registered Agent deployment mode does not match requested install token");
        }
        var rawToken = secretHasher.generateSecret();
        var entity = installTokenRepository.save(new ArchDoxAgentInstallToken(
                officeId,
                agent.id(),
                secretHasher.hash(rawToken),
                now.plusMinutes(ttlMinutes),
                userId,
                now));
        return new IssuedArchDoxAgentInstallToken(agent, entity, rawToken);
    }

    @Transactional
    public AgentConnection connect(AgentHello hello) {
        return switch (resolveAuthMode(hello)) {
            case INSTALL_TOKEN -> connectWithInstallToken(hello);
            case DEVICE_SECRET -> connectWithDeviceSecret(hello);
            case SHARED_SECRET -> connectWithSharedSecret(hello);
        };
    }

    @Transactional
    public ArchDoxAgent authenticateDevice(Long agentId, String deviceSecret) {
        if (agentId == null || deviceSecret == null || deviceSecret.isBlank()) {
            throw new UnauthorizedException("Agent device credentials are required");
        }
        var agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new UnauthorizedException("Agent not found"));
        if (!secretHasher.matches(deviceSecret, agent.deviceSecretHash())) {
            throw new UnauthorizedException("Invalid agent device secret");
        }
        agent.markAuthenticated(OffsetDateTime.now());
        return agent;
    }

    @Transactional
    public Long authenticateDownload(
            Long agentId,
            String deviceSecret,
            String sharedSecret,
            Long officeId
    ) {
        if (agentId != null || deviceSecret != null) {
            var agent = authenticateDevice(agentId, deviceSecret);
            if (!agent.officeId().equals(officeId)) {
                throw new UnauthorizedException("Agent does not belong to this office");
            }
            return agent.officeId();
        }
        if (properties.isAllowSharedSecretAuth() && properties.getSharedSecret().equals(sharedSecret)) {
            return officeId;
        }
        throw new UnauthorizedException("Invalid agent credentials");
    }

    private AgentConnection connectWithInstallToken(AgentHello hello) {
        requireInstallHello(hello);
        if (!officeRepository.existsById(hello.officeId())) {
            throw new NotFoundException("Office not found");
        }
        var now = OffsetDateTime.now();
        var token = installTokenRepository.findByTokenHash(secretHasher.hash(installTokenValue(hello)))
                .orElseThrow(() -> new UnauthorizedException("Invalid agent install token"));
        if (!token.officeId().equals(hello.officeId())) {
            throw new UnauthorizedException("Install token does not belong to this office");
        }
        if (token.agentId() == null) {
            throw new UnauthorizedException("Install token is not bound to a registered Agent");
        }
        var agent = agentRepository.findById(token.agentId())
                .orElseThrow(() -> new UnauthorizedException("Registered Agent not found"));
        if (!agent.officeId().equals(hello.officeId())) {
            throw new UnauthorizedException("Registered Agent does not belong to this office");
        }
        if (!agent.agentCode().equals(hello.agentCode().trim())) {
            throw new UnauthorizedException("Install token is not issued for this Agent code");
        }
        if (agent.deploymentMode() != deploymentMode(hello.deploymentMode())) {
            throw new UnauthorizedException("Install token is not issued for this deployment mode");
        }
        if (!token.canUse(now)) {
            if (token.status() == ArchDoxAgentInstallTokenStatus.ACTIVE) {
                token.markExpired(now);
            }
            throw new UnauthorizedException("Agent install token is expired or already used");
        }
        token.markUsed(now);

        var deviceSecret = secretHasher.generateSecret();
        var compatibility = compatibilityService.evaluate(hello);
        agent.pairDeviceSecret(
                secretHasher.hash(deviceSecret),
                agent.deploymentMode(),
                hello.version(),
                compatibilityService.capabilitiesWithCompatibility(hello, compatibility),
                hello.storageProfile(),
                now);
        return new AgentConnection(agent, deviceSecret, compatibility);
    }

    private AgentConnection connectWithDeviceSecret(AgentHello hello) {
        if (hello.agentId() == null) {
            throw new BadRequestException("agentId is required for DEVICE_SECRET auth");
        }
        var agent = authenticateDevice(hello.agentId(), hello.deviceSecret());
        var requestedDeploymentMode = deploymentModeOrRegistered(agent, hello.deploymentMode());
        var compatibility = compatibilityService.evaluate(hello);
        agent.markOnline(
                requestedDeploymentMode,
                hello.version(),
                compatibilityService.capabilitiesWithCompatibility(hello, compatibility),
                hello.storageProfile(),
                OffsetDateTime.now());
        return new AgentConnection(agent, null, compatibility);
    }

    private AgentConnection connectWithSharedSecret(AgentHello hello) {
        if (!properties.isAllowSharedSecretAuth()) {
            throw new UnauthorizedException("Shared-secret agent authentication is disabled");
        }
        if (hello.officeId() == null || hello.agentCode() == null || hello.agentCode().isBlank()) {
            throw new BadRequestException("officeId and agentCode are required");
        }
        if (!properties.getSharedSecret().equals(hello.token())) {
            throw new UnauthorizedException("Invalid agent token");
        }
        if (!officeRepository.existsById(hello.officeId())) {
            throw new NotFoundException("Office not found");
        }
        var now = OffsetDateTime.now();
        var compatibility = compatibilityService.evaluate(hello);
        var agent = agentRepository.findByOfficeIdAndAgentCode(hello.officeId(), hello.agentCode().trim())
                .orElseGet(() -> agentRepository.save(new ArchDoxAgent(
                        hello.officeId(),
                        hello.agentCode().trim(),
                        deploymentMode(hello),
                        hello.version(),
                        compatibilityService.capabilitiesWithCompatibility(hello, compatibility),
                        hello.storageProfile(),
                        now)));
        agent.markOnline(
                deploymentMode(hello),
                hello.version(),
                compatibilityService.capabilitiesWithCompatibility(hello, compatibility),
                hello.storageProfile(),
                now);
        agent.markSharedSecretAuthenticated(now);
        return new AgentConnection(agent, null, compatibility);
    }

    private ArchDoxAgentAuthMode resolveAuthMode(AgentHello hello) {
        if (hello.authMode() != null && !hello.authMode().isBlank()) {
            return ArchDoxAgentAuthMode.valueOf(hello.authMode().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (hello.agentId() != null || (hello.deviceSecret() != null && !hello.deviceSecret().isBlank())) {
            return ArchDoxAgentAuthMode.DEVICE_SECRET;
        }
        if (hello.installToken() != null && !hello.installToken().isBlank()) {
            return ArchDoxAgentAuthMode.INSTALL_TOKEN;
        }
        return ArchDoxAgentAuthMode.SHARED_SECRET;
    }

    private void requireInstallHello(AgentHello hello) {
        if (hello.officeId() == null || hello.agentCode() == null || hello.agentCode().isBlank()) {
            throw new BadRequestException("officeId and agentCode are required");
        }
        if (installTokenValue(hello) == null || installTokenValue(hello).isBlank()) {
            throw new BadRequestException("installToken is required");
        }
    }

    private String installTokenValue(AgentHello hello) {
        return hello.installToken() == null ? hello.token() : hello.installToken();
    }

    private ArchDoxAgentDeploymentMode deploymentMode(AgentHello hello) {
        return deploymentMode(hello.deploymentMode());
    }

    private ArchDoxAgentDeploymentMode deploymentMode(String deploymentMode) {
        if (deploymentMode == null || deploymentMode.isBlank()) {
            return ArchDoxAgentDeploymentMode.LOCAL_OFFICE;
        }
        return ArchDoxAgentDeploymentMode.valueOf(deploymentMode.trim().toUpperCase(Locale.ROOT));
    }

    private ArchDoxAgentDeploymentMode deploymentModeOrRegistered(ArchDoxAgent agent, String deploymentMode) {
        if (deploymentMode == null || deploymentMode.isBlank()) {
            return agent.deploymentMode();
        }
        var requestedDeploymentMode = deploymentMode(deploymentMode);
        if (requestedDeploymentMode != agent.deploymentMode()) {
            throw new UnauthorizedException("Agent deployment mode does not match registered Agent");
        }
        return requestedDeploymentMode;
    }

    private String normalizedAgentCode(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            return "office-main";
        }
        return agentCode.trim().toLowerCase(Locale.ROOT);
    }

    private void requireOfficeAdmin(Long userId, Long officeId) {
        var membership = membershipRepository.findByUserIdAndOfficeIdAndStatus(
                        userId,
                        officeId,
                        MembershipStatus.ACTIVE)
                .orElseThrow(() -> new UnauthorizedException("Office membership required"));
        if (membership.role() != MembershipRole.OWNER && membership.role() != MembershipRole.ADMIN) {
            throw new UnauthorizedException("Office admin role required");
        }
    }
}

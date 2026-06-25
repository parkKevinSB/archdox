package com.archdox.cloud.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "archdox_agents")
public class ArchDoxAgent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "agent_code", nullable = false)
    private String agentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_mode", nullable = false)
    private ArchDoxAgentDeploymentMode deploymentMode = ArchDoxAgentDeploymentMode.LOCAL_OFFICE;

    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArchDoxAgentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_mode", nullable = false)
    private ArchDoxAgentAuthMode authMode = ArchDoxAgentAuthMode.SHARED_SECRET;

    @Column(name = "device_secret_hash")
    private String deviceSecretHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities_json", columnDefinition = "jsonb")
    private Map<String, Object> capabilitiesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "storage_profile_json", columnDefinition = "jsonb")
    private Map<String, Object> storageProfileJson;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "paired_at")
    private OffsetDateTime pairedAt;

    @Column(name = "last_authenticated_at")
    private OffsetDateTime lastAuthenticatedAt;

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ArchDoxAgent() {
    }

    public ArchDoxAgent(Long officeId, String agentCode, String version, Map<String, Object> capabilities, OffsetDateTime now) {
        this(
                officeId,
                agentCode,
                ArchDoxAgentDeploymentMode.LOCAL_OFFICE,
                version,
                capabilities,
                Map.of(),
                now);
    }

    public ArchDoxAgent(
            Long officeId,
            String agentCode,
            ArchDoxAgentDeploymentMode deploymentMode,
            String version,
            Map<String, Object> capabilities,
            Map<String, Object> storageProfile,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.agentCode = agentCode;
        this.deploymentMode = deploymentMode == null ? ArchDoxAgentDeploymentMode.LOCAL_OFFICE : deploymentMode;
        this.version = version;
        this.status = ArchDoxAgentStatus.ONLINE;
        this.authMode = ArchDoxAgentAuthMode.SHARED_SECRET;
        this.capabilitiesJson = capabilities;
        this.storageProfileJson = storageProfile;
        this.lastSeenAt = now;
        this.lastAuthenticatedAt = now;
        this.registeredAt = now;
        this.updatedAt = now;
    }

    public static ArchDoxAgent registerPendingPair(
            Long officeId,
            String agentCode,
            ArchDoxAgentDeploymentMode deploymentMode,
            OffsetDateTime now
    ) {
        var agent = new ArchDoxAgent(
                officeId,
                agentCode,
                deploymentMode,
                null,
                Map.of(),
                Map.of(),
                now);
        agent.status = ArchDoxAgentStatus.PENDING_PAIR;
        agent.authMode = ArchDoxAgentAuthMode.INSTALL_TOKEN;
        agent.lastSeenAt = null;
        agent.lastAuthenticatedAt = null;
        return agent;
    }

    public void markOnline(
            ArchDoxAgentDeploymentMode deploymentMode,
            String version,
            Map<String, Object> capabilities,
            Map<String, Object> storageProfile,
            OffsetDateTime now
    ) {
        this.deploymentMode = deploymentMode == null ? ArchDoxAgentDeploymentMode.LOCAL_OFFICE : deploymentMode;
        this.version = version;
        this.capabilitiesJson = capabilities;
        this.storageProfileJson = storageProfile;
        this.status = ArchDoxAgentStatus.ONLINE;
        this.lastSeenAt = now;
        this.updatedAt = now;
    }

    public void markOnline(String version, Map<String, Object> capabilities, OffsetDateTime now) {
        markOnline(ArchDoxAgentDeploymentMode.LOCAL_OFFICE, version, capabilities, Map.of(), now);
    }

    public void pairDeviceSecret(
            String deviceSecretHash,
            ArchDoxAgentDeploymentMode deploymentMode,
            String version,
            Map<String, Object> capabilities,
            Map<String, Object> storageProfile,
            OffsetDateTime now
    ) {
        this.deviceSecretHash = deviceSecretHash;
        this.authMode = ArchDoxAgentAuthMode.DEVICE_SECRET;
        this.pairedAt = now;
        markOnline(deploymentMode, version, capabilities, storageProfile, now);
        markAuthenticated(now);
    }

    public void provisionDeviceSecret(
            String deviceSecretHash,
            ArchDoxAgentDeploymentMode deploymentMode,
            Map<String, Object> storageProfile,
            OffsetDateTime now
    ) {
        this.deviceSecretHash = deviceSecretHash;
        this.authMode = ArchDoxAgentAuthMode.DEVICE_SECRET;
        this.deploymentMode = deploymentMode == null ? ArchDoxAgentDeploymentMode.CLOUD_MANAGED : deploymentMode;
        this.storageProfileJson = storageProfile == null ? Map.of() : storageProfile;
        this.status = ArchDoxAgentStatus.OFFLINE;
        this.pairedAt = now;
        this.lastAuthenticatedAt = null;
        this.updatedAt = now;
    }

    public void pairDeviceSecret(String deviceSecretHash, String version, Map<String, Object> capabilities, OffsetDateTime now) {
        pairDeviceSecret(
                deviceSecretHash,
                ArchDoxAgentDeploymentMode.LOCAL_OFFICE,
                version,
                capabilities,
                Map.of(),
                now);
    }

    public void markSharedSecretAuthenticated(OffsetDateTime now) {
        this.authMode = ArchDoxAgentAuthMode.SHARED_SECRET;
        markAuthenticated(now);
    }

    public void markAuthenticated(OffsetDateTime now) {
        this.lastAuthenticatedAt = now;
        this.updatedAt = now;
    }

    public void markSeen(OffsetDateTime now) {
        this.lastSeenAt = now;
        this.updatedAt = now;
        if (status == ArchDoxAgentStatus.OFFLINE || status == ArchDoxAgentStatus.PENDING_PAIR) {
            this.status = ArchDoxAgentStatus.ONLINE;
        }
    }

    public void markOffline(OffsetDateTime now) {
        this.status = ArchDoxAgentStatus.OFFLINE;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public String agentCode() {
        return agentCode;
    }

    public ArchDoxAgentDeploymentMode deploymentMode() {
        return deploymentMode;
    }

    public ArchDoxAgentStatus status() {
        return status;
    }

    public ArchDoxAgentAuthMode authMode() {
        return authMode;
    }

    public String deviceSecretHash() {
        return deviceSecretHash;
    }

    public String version() {
        return version;
    }

    public Map<String, Object> capabilitiesJson() {
        return capabilitiesJson;
    }

    public Map<String, Object> storageProfileJson() {
        return storageProfileJson;
    }

    public OffsetDateTime lastSeenAt() {
        return lastSeenAt;
    }

    public OffsetDateTime pairedAt() {
        return pairedAt;
    }

    public OffsetDateTime lastAuthenticatedAt() {
        return lastAuthenticatedAt;
    }

    public OffsetDateTime registeredAt() {
        return registeredAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}

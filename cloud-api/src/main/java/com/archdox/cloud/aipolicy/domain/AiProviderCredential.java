package com.archdox.cloud.aipolicy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ai_provider_credentials")
public class AiProviderCredential {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_code", nullable = false, unique = true)
    private String providerCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private AiProviderType providerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiProviderCredentialStatus status = AiProviderCredentialStatus.DRAFT;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "default_model")
    private String defaultModel;

    @Column(name = "encrypted_api_key")
    private String encryptedApiKey;

    @Column(name = "api_key_fingerprint")
    private String apiKeyFingerprint;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion = 1;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected AiProviderCredential() {
    }

    public AiProviderCredential(
            String providerCode,
            String displayName,
            AiProviderType providerType,
            String baseUrl,
            String defaultModel,
            String encryptedApiKey,
            String apiKeyFingerprint,
            Long createdByUserId,
            OffsetDateTime now
    ) {
        this.providerCode = providerCode;
        this.displayName = displayName;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.encryptedApiKey = encryptedApiKey;
        this.apiKeyFingerprint = apiKeyFingerprint;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String displayName,
            AiProviderType providerType,
            String baseUrl,
            String defaultModel,
            boolean updateApiKey,
            String encryptedApiKey,
            String apiKeyFingerprint,
            OffsetDateTime now
    ) {
        this.displayName = displayName;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        if (updateApiKey) {
            this.encryptedApiKey = encryptedApiKey;
            this.apiKeyFingerprint = apiKeyFingerprint;
            this.credentialVersion++;
        }
        this.updatedAt = now;
    }

    public void publish(OffsetDateTime now) {
        this.status = AiProviderCredentialStatus.ACTIVE;
        this.credentialVersion++;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void disable(OffsetDateTime now) {
        this.status = AiProviderCredentialStatus.DISABLED;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public String providerCode() {
        return providerCode;
    }

    public String displayName() {
        return displayName;
    }

    public AiProviderType providerType() {
        return providerType;
    }

    public AiProviderCredentialStatus status() {
        return status;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public String encryptedApiKey() {
        return encryptedApiKey;
    }

    public String apiKeyFingerprint() {
        return apiKeyFingerprint;
    }

    public long credentialVersion() {
        return credentialVersion;
    }

    public Long createdByUserId() {
        return createdByUserId;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    public OffsetDateTime publishedAt() {
        return publishedAt;
    }
}

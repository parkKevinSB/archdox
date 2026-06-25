package com.archdox.cloud.officestorage.domain;

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
@Table(name = "office_storage_profiles")
public class OfficeStorageProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "profile_code", nullable = false)
    private String profileCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private OfficeStorageProviderType providerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfficeStorageProfileStatus status = OfficeStorageProfileStatus.DRAFT;

    @Column(name = "endpoint")
    private String endpoint;

    @Column(name = "region", nullable = false)
    private String region;

    @Column(name = "bucket_name", nullable = false)
    private String bucketName;

    @Column(name = "object_prefix")
    private String objectPrefix;

    @Column(name = "path_style_access", nullable = false)
    private boolean pathStyleAccess;

    @Column(name = "encrypted_access_key", columnDefinition = "text")
    private String encryptedAccessKey;

    @Column(name = "encrypted_secret_key", columnDefinition = "text")
    private String encryptedSecretKey;

    @Column(name = "access_key_fingerprint")
    private String accessKeyFingerprint;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion = 1;

    @Column(name = "last_tested_at")
    private OffsetDateTime lastTestedAt;

    @Column(name = "last_test_status")
    private String lastTestStatus;

    @Column(name = "last_test_message", columnDefinition = "text")
    private String lastTestMessage;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected OfficeStorageProfile() {
    }

    public OfficeStorageProfile(
            Long officeId,
            String profileCode,
            String displayName,
            OfficeStorageProviderType providerType,
            String endpoint,
            String region,
            String bucketName,
            String objectPrefix,
            boolean pathStyleAccess,
            String encryptedAccessKey,
            String encryptedSecretKey,
            String accessKeyFingerprint,
            Long createdByUserId,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.profileCode = profileCode;
        this.displayName = displayName;
        this.providerType = providerType;
        this.endpoint = endpoint;
        this.region = region;
        this.bucketName = bucketName;
        this.objectPrefix = objectPrefix;
        this.pathStyleAccess = pathStyleAccess;
        this.encryptedAccessKey = encryptedAccessKey;
        this.encryptedSecretKey = encryptedSecretKey;
        this.accessKeyFingerprint = accessKeyFingerprint;
        this.createdByUserId = createdByUserId;
        this.updatedByUserId = createdByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateSettings(
            String displayName,
            OfficeStorageProviderType providerType,
            String endpoint,
            String region,
            String bucketName,
            String objectPrefix,
            boolean pathStyleAccess,
            boolean updateCredentials,
            String encryptedAccessKey,
            String encryptedSecretKey,
            String accessKeyFingerprint,
            Long updatedByUserId,
            OffsetDateTime now
    ) {
        this.displayName = displayName;
        this.providerType = providerType;
        this.endpoint = endpoint;
        this.region = region;
        this.bucketName = bucketName;
        this.objectPrefix = objectPrefix;
        this.pathStyleAccess = pathStyleAccess;
        if (updateCredentials) {
            this.encryptedAccessKey = encryptedAccessKey;
            this.encryptedSecretKey = encryptedSecretKey;
            this.accessKeyFingerprint = accessKeyFingerprint;
            this.credentialVersion++;
        }
        this.status = OfficeStorageProfileStatus.DRAFT;
        this.lastTestStatus = null;
        this.lastTestMessage = null;
        this.updatedByUserId = updatedByUserId;
        this.updatedAt = now;
    }

    public void markConnectionTest(
            OfficeStorageConnectionTestStatus testStatus,
            String message,
            OffsetDateTime now
    ) {
        this.lastTestedAt = now;
        this.lastTestStatus = testStatus.name();
        this.lastTestMessage = message;
        this.status = testStatus == OfficeStorageConnectionTestStatus.SUCCEEDED
                ? OfficeStorageProfileStatus.VERIFIED
                : OfficeStorageProfileStatus.FAILED;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public String profileCode() {
        return profileCode;
    }

    public String displayName() {
        return displayName;
    }

    public OfficeStorageProviderType providerType() {
        return providerType;
    }

    public OfficeStorageProfileStatus status() {
        return status;
    }

    public String endpoint() {
        return endpoint;
    }

    public String region() {
        return region;
    }

    public String bucketName() {
        return bucketName;
    }

    public String objectPrefix() {
        return objectPrefix;
    }

    public boolean pathStyleAccess() {
        return pathStyleAccess;
    }

    public String encryptedAccessKey() {
        return encryptedAccessKey;
    }

    public String encryptedSecretKey() {
        return encryptedSecretKey;
    }

    public String accessKeyFingerprint() {
        return accessKeyFingerprint;
    }

    public long credentialVersion() {
        return credentialVersion;
    }

    public OffsetDateTime lastTestedAt() {
        return lastTestedAt;
    }

    public String lastTestStatus() {
        return lastTestStatus;
    }

    public String lastTestMessage() {
        return lastTestMessage;
    }

    public Long createdByUserId() {
        return createdByUserId;
    }

    public Long updatedByUserId() {
        return updatedByUserId;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}

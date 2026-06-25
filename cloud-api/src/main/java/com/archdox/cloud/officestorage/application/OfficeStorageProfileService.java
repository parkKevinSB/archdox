package com.archdox.cloud.officestorage.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.officestorage.domain.OfficeStorageConnectionTestStatus;
import com.archdox.cloud.officestorage.domain.OfficeStorageProfile;
import com.archdox.cloud.officestorage.domain.OfficeStorageProviderType;
import com.archdox.cloud.officestorage.dto.OfficeStorageConnectionTestResponse;
import com.archdox.cloud.officestorage.dto.OfficeStorageProfileResponse;
import com.archdox.cloud.officestorage.dto.SaveOfficeStorageProfileRequest;
import com.archdox.cloud.officestorage.infra.OfficeStorageProfileRepository;
import com.archdox.cloud.officeops.application.OfficeAdminAccessService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfficeStorageProfileService {
    private static final String DEFAULT_PROFILE_CODE = "default";

    private final OfficeStorageProfileRepository repository;
    private final OfficeAdminAccessService officeAdminAccessService;
    private final OfficeStorageCredentialCipher credentialCipher;
    private final OfficeStorageConnectionTester connectionTester;

    public OfficeStorageProfileService(
            OfficeStorageProfileRepository repository,
            OfficeAdminAccessService officeAdminAccessService,
            OfficeStorageCredentialCipher credentialCipher,
            OfficeStorageConnectionTester connectionTester
    ) {
        this.repository = repository;
        this.officeAdminAccessService = officeAdminAccessService;
        this.credentialCipher = credentialCipher;
        this.connectionTester = connectionTester;
    }

    @Transactional(readOnly = true)
    public List<OfficeStorageProfileResponse> list(UserPrincipal principal) {
        var officeId = officeAdminAccessService.requireOfficeAdmin(principal);
        return repository.findByOfficeIdOrderByCreatedAtDesc(officeId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OfficeStorageProfileResponse save(UserPrincipal principal, SaveOfficeStorageProfileRequest request) {
        var officeId = officeAdminAccessService.requireOfficeAdmin(principal);
        var now = OffsetDateTime.now();
        var normalized = normalize(request);
        var existing = findExisting(officeId, request.id(), normalized.profileCode());
        var updateCredentials = hasText(request.accessKey()) || hasText(request.secretKey());
        if (existing == null && !updateCredentials) {
            throw new BadRequestException("S3 access key and secret key are required");
        }
        if (updateCredentials && (!hasText(request.accessKey()) || !hasText(request.secretKey()))) {
            throw new BadRequestException("Both S3 access key and secret key are required when changing credentials");
        }
        var encryptedAccessKey = updateCredentials ? credentialCipher.encrypt(request.accessKey().trim()) : null;
        var encryptedSecretKey = updateCredentials ? credentialCipher.encrypt(request.secretKey().trim()) : null;
        var accessKeyFingerprint = updateCredentials ? credentialCipher.fingerprint(request.accessKey().trim()) : null;

        if (existing == null) {
            var created = new OfficeStorageProfile(
                    officeId,
                    normalized.profileCode(),
                    normalized.displayName(),
                    normalized.providerType(),
                    normalized.endpoint(),
                    normalized.region(),
                    normalized.bucketName(),
                    normalized.objectPrefix(),
                    normalized.pathStyleAccess(),
                    encryptedAccessKey,
                    encryptedSecretKey,
                    accessKeyFingerprint,
                    principal.userId(),
                    now);
            return toResponse(repository.save(created));
        }

        existing.updateSettings(
                normalized.displayName(),
                normalized.providerType(),
                normalized.endpoint(),
                normalized.region(),
                normalized.bucketName(),
                normalized.objectPrefix(),
                normalized.pathStyleAccess(),
                updateCredentials,
                encryptedAccessKey,
                encryptedSecretKey,
                accessKeyFingerprint,
                principal.userId(),
                now);
        return toResponse(existing);
    }

    @Transactional
    public OfficeStorageConnectionTestResponse test(UserPrincipal principal, Long profileId) {
        var officeId = officeAdminAccessService.requireOfficeAdmin(principal);
        var profile = repository.findByOfficeIdAndId(officeId, profileId)
                .orElseThrow(() -> new NotFoundException("Office storage profile not found"));
        var result = connectionTester.test(toConnectionProfile(profile));
        var now = OffsetDateTime.now();
        profile.markConnectionTest(result.status(), result.message(), now);
        return new OfficeStorageConnectionTestResponse(profile.id(), result.status(), result.message(), result.elapsedMs(), now);
    }

    private OfficeStorageProfile findExisting(Long officeId, Long id, String profileCode) {
        if (id != null) {
            return repository.findByOfficeIdAndId(officeId, id)
                    .orElseThrow(() -> new NotFoundException("Office storage profile not found"));
        }
        return repository.findByOfficeIdAndProfileCode(officeId, profileCode).orElse(null);
    }

    private OfficeStorageConnectionProfile toConnectionProfile(OfficeStorageProfile profile) {
        return new OfficeStorageConnectionProfile(
                profile.providerType(),
                profile.endpoint(),
                profile.region(),
                profile.bucketName(),
                profile.objectPrefix(),
                profile.pathStyleAccess(),
                credentialCipher.decrypt(profile.encryptedAccessKey()),
                credentialCipher.decrypt(profile.encryptedSecretKey()));
    }

    private OfficeStorageProfileResponse toResponse(OfficeStorageProfile profile) {
        return new OfficeStorageProfileResponse(
                profile.id(),
                profile.officeId(),
                profile.profileCode(),
                profile.displayName(),
                profile.providerType(),
                profile.status(),
                profile.endpoint(),
                profile.region(),
                profile.bucketName(),
                profile.objectPrefix(),
                profile.pathStyleAccess(),
                hasText(profile.encryptedAccessKey()) && hasText(profile.encryptedSecretKey()),
                profile.accessKeyFingerprint(),
                credentialCipher.maskFingerprint(profile.accessKeyFingerprint()),
                profile.credentialVersion(),
                profile.lastTestedAt(),
                profile.lastTestStatus(),
                profile.lastTestMessage(),
                profile.createdAt(),
                profile.updatedAt());
    }

    private NormalizedProfile normalize(SaveOfficeStorageProfileRequest request) {
        var providerType = request.providerType() == null ? OfficeStorageProviderType.AWS_S3 : request.providerType();
        var profileCode = normalizeCode(request.profileCode(), DEFAULT_PROFILE_CODE);
        var displayName = normalizeText(request.displayName());
        if (!hasText(displayName)) {
            displayName = defaultDisplayName(providerType);
        }
        var region = normalizeText(request.region());
        if (!hasText(region)) {
            region = "ap-northeast-2";
        }
        var bucketName = normalizeText(request.bucketName());
        if (!hasText(bucketName)) {
            throw new BadRequestException("S3 bucket is required");
        }
        var endpoint = normalizeEndpoint(providerType, request.endpoint());
        var objectPrefix = normalizePrefix(request.objectPrefix());
        var pathStyleAccess = request.pathStyleAccess() != null
                ? request.pathStyleAccess()
                : providerType != OfficeStorageProviderType.AWS_S3;
        if (providerType != OfficeStorageProviderType.AWS_S3 && !hasText(endpoint)) {
            throw new BadRequestException("S3-compatible endpoint is required");
        }
        return new NormalizedProfile(
                profileCode,
                displayName,
                providerType,
                endpoint,
                region,
                bucketName,
                objectPrefix,
                pathStyleAccess);
    }

    private String defaultDisplayName(OfficeStorageProviderType providerType) {
        return switch (providerType) {
            case AWS_S3 -> "AWS S3";
            case MINIO -> "MinIO";
            case CUSTOM_S3 -> "S3 호환 저장소";
        };
    }

    private String normalizeEndpoint(OfficeStorageProviderType providerType, String endpoint) {
        var normalized = normalizeText(endpoint);
        if (providerType == OfficeStorageProviderType.AWS_S3) {
            return normalized;
        }
        if (!hasText(normalized)) {
            return null;
        }
        return normalized;
    }

    private String normalizeCode(String value, String fallback) {
        var normalized = normalizeText(value);
        if (!hasText(normalized)) {
            return fallback;
        }
        return normalized.toLowerCase().replaceAll("[^a-z0-9_-]", "-");
    }

    private String normalizePrefix(String value) {
        var normalized = normalizeText(value);
        if (!hasText(normalized)) {
            return null;
        }
        normalized = normalized.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record NormalizedProfile(
            String profileCode,
            String displayName,
            OfficeStorageProviderType providerType,
            String endpoint,
            String region,
            String bucketName,
            String objectPrefix,
            boolean pathStyleAccess
    ) {
    }
}

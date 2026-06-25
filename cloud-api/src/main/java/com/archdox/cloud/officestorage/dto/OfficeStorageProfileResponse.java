package com.archdox.cloud.officestorage.dto;

import com.archdox.cloud.officestorage.domain.OfficeStorageProfileStatus;
import com.archdox.cloud.officestorage.domain.OfficeStorageProviderType;
import java.time.OffsetDateTime;

public record OfficeStorageProfileResponse(
        Long id,
        Long officeId,
        String profileCode,
        String displayName,
        OfficeStorageProviderType providerType,
        OfficeStorageProfileStatus status,
        String endpoint,
        String region,
        String bucketName,
        String objectPrefix,
        boolean pathStyleAccess,
        boolean credentialsConfigured,
        String accessKeyFingerprint,
        String maskedAccessKeyFingerprint,
        long credentialVersion,
        OffsetDateTime lastTestedAt,
        String lastTestStatus,
        String lastTestMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

package com.archdox.cloud.officestorage.dto;

import com.archdox.cloud.officestorage.domain.OfficeStorageProviderType;

public record SaveOfficeStorageProfileRequest(
        Long id,
        String profileCode,
        String displayName,
        OfficeStorageProviderType providerType,
        String endpoint,
        String region,
        String bucketName,
        String objectPrefix,
        Boolean pathStyleAccess,
        String accessKey,
        String secretKey
) {
}

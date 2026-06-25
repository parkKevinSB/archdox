package com.archdox.cloud.officestorage.application;

import com.archdox.cloud.officestorage.domain.OfficeStorageProviderType;

public record OfficeStorageConnectionProfile(
        OfficeStorageProviderType providerType,
        String endpoint,
        String region,
        String bucketName,
        String objectPrefix,
        boolean pathStyleAccess,
        String accessKey,
        String secretKey
) {
}

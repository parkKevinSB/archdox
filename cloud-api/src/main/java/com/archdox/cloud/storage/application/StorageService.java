package com.archdox.cloud.storage.application;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;

public interface StorageService {
    StorageType storageType();

    default boolean supports(StorageType storageType) {
        return storageType() == storageType;
    }

    StorageObjectRef put(
            String objectKey,
            String originalFileName,
            String contentType,
            long size,
            InputStream input
    ) throws IOException;

    InputStream open(String objectKey) throws IOException;

    void deleteIfExists(String objectKey) throws IOException;

    default StorageUploadUrl createPresignedPutUrl(String objectKey, String contentType, OffsetDateTime expiresAt) {
        throw new UnsupportedOperationException("Presigned upload is not supported by " + storageType());
    }
}

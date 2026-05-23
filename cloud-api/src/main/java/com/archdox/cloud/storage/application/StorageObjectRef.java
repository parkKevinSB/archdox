package com.archdox.cloud.storage.application;

public record StorageObjectRef(
        String fileId,
        StorageType storageType,
        String bucketName,
        String objectKey,
        String originalFileName,
        String contentType,
        long size
) {
}

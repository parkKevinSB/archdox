package com.archdox.cloud.storage.application;

import org.springframework.stereotype.Component;

@Component
public class NasStorageService extends FileSystemStorageSupport {
    public NasStorageService(StorageProperties properties) {
        super(
                StorageType.NAS,
                properties.getNas().getBucketName(),
                properties.getNas().getRootPath());
    }
}

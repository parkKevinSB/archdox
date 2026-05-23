package com.archdox.cloud.storage.application;

import org.springframework.stereotype.Component;

@Component
public class LocalFileStorageService extends FileSystemStorageSupport {
    public LocalFileStorageService(StorageProperties properties) {
        super(
                StorageType.LOCAL_FILE,
                properties.getLocalFile().getBucketName(),
                properties.getLocalFile().getRootPath());
    }
}

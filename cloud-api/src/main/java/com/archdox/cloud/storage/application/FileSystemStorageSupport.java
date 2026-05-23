package com.archdox.cloud.storage.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

abstract class FileSystemStorageSupport implements StorageService {
    private final StorageType storageType;
    private final String bucketName;
    private final Path root;

    FileSystemStorageSupport(StorageType storageType, String bucketName, String rootPath) {
        this.storageType = storageType;
        this.bucketName = bucketName;
        this.root = Paths.get(rootPath).toAbsolutePath().normalize();
    }

    @Override
    public StorageType storageType() {
        return storageType;
    }

    @Override
    public StorageObjectRef put(
            String objectKey,
            String originalFileName,
            String contentType,
            long size,
            InputStream input
    ) throws IOException {
        var target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new StorageObjectRef(
                UUID.randomUUID().toString(),
                storageType,
                bucketName,
                objectKey,
                originalFileName,
                contentType,
                size);
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
        return Files.newInputStream(resolve(objectKey));
    }

    @Override
    public void deleteIfExists(String objectKey) throws IOException {
        Files.deleteIfExists(resolve(objectKey));
    }

    private Path resolve(String objectKey) {
        var target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage object key");
        }
        return target;
    }
}

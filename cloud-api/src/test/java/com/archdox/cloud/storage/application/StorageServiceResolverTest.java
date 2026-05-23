package com.archdox.cloud.storage.application;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.archdox.cloud.global.api.BadRequestException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class StorageServiceResolverTest {
    @Test
    void resolvesConfiguredActiveStorageType() {
        var properties = new StorageProperties();
        properties.setActive(StorageType.NAS);
        var local = new StubStorageService(StorageType.LOCAL_FILE);
        var nas = new StubStorageService(StorageType.NAS);
        var resolver = new StorageServiceResolver(properties, List.of(local, nas));

        assertSame(nas, resolver.active());
        assertSame(local, resolver.forType(StorageType.LOCAL_FILE));
    }

    @Test
    void rejectsUnsupportedStorageType() {
        var properties = new StorageProperties();
        var resolver = new StorageServiceResolver(properties, List.of(new StubStorageService(StorageType.LOCAL_FILE)));

        assertThrows(BadRequestException.class, () -> resolver.forType(StorageType.S3_COMPATIBLE));
    }

    private record StubStorageService(StorageType storageType) implements StorageService {
        @Override
        public StorageObjectRef put(
                String objectKey,
                String originalFileName,
                String contentType,
                long size,
                InputStream input
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream open(String objectKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteIfExists(String objectKey) {
            throw new UnsupportedOperationException();
        }
    }
}

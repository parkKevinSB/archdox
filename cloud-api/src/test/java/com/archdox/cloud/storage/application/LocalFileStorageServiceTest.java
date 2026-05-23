package com.archdox.cloud.storage.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void storesAndReadsFileUnderConfiguredRoot() throws Exception {
        var properties = new StorageProperties();
        properties.getLocalFile().setRootPath(tempDir.toString());
        properties.getLocalFile().setBucketName("local-test");
        var service = new LocalFileStorageService(properties);

        var bytes = "hello archdox".getBytes(StandardCharsets.UTF_8);
        var ref = service.put(
                "offices/1/documents/a.txt",
                "a.txt",
                "text/plain",
                bytes.length,
                new ByteArrayInputStream(bytes));

        assertEquals(StorageType.LOCAL_FILE, ref.storageType());
        assertEquals("local-test", ref.bucketName());
        assertEquals("offices/1/documents/a.txt", ref.objectKey());
        assertEquals("hello archdox", new String(service.open(ref.objectKey()).readAllBytes(), StandardCharsets.UTF_8));

        service.deleteIfExists(ref.objectKey());
        assertFalse(Files.exists(tempDir.resolve("offices/1/documents/a.txt")));
    }

    @Test
    void rejectsObjectKeyEscapingConfiguredRoot() {
        var properties = new StorageProperties();
        properties.getLocalFile().setRootPath(tempDir.toString());
        var service = new LocalFileStorageService(properties);

        assertThrows(IllegalArgumentException.class, () -> service.put(
                "../escape.txt",
                "escape.txt",
                "text/plain",
                1,
                new ByteArrayInputStream(new byte[] {1})));
    }
}

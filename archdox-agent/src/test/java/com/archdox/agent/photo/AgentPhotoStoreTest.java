package com.archdox.agent.photo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import com.archdox.agent.cloud.ArchDoxAgentProperties.StorageTarget;
import com.archdox.agent.storage.AgentS3ObjectGateway;
import com.archdox.agent.storage.AgentStorageServiceFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentPhotoStoreTest {
    @Test
    void storesOriginalInsideConfiguredRoot(@TempDir Path tempDir) throws Exception {
        var properties = new ArchDoxAgentProperties();
        properties.setLocalStorageRoot(tempDir.toString());
        var store = new AgentPhotoStore(properties);
        var logicalRef = "offices/10/reports/100/photos/photo-1/original.jpg";

        var stored = store.storeOriginal(logicalRef, new ByteArrayInputStream("photo".getBytes()));

        assertEquals(logicalRef, stored.logicalRef());
        assertEquals(5, stored.bytes());
        assertTrue(Files.exists(tempDir.resolve(logicalRef)));
        assertEquals("photo", Files.readString(tempDir.resolve(logicalRef)));
    }

    @Test
    void rejectsPathTraversal(@TempDir Path tempDir) {
        var properties = new ArchDoxAgentProperties();
        properties.setLocalStorageRoot(tempDir.toString());
        var store = new AgentPhotoStore(properties);

        assertThrows(IllegalArgumentException.class,
                () -> store.storeOriginal("../outside.jpg", new ByteArrayInputStream("photo".getBytes())));
    }

    @Test
    void supportsNasProfileAsFileSystemRoot(@TempDir Path tempDir) throws Exception {
        var properties = new ArchDoxAgentProperties();
        var original = new StorageTarget();
        original.setKind("NAS");
        original.setRootPath(tempDir.resolve("office-share").toString());
        properties.getStorage().setOriginal(original);
        var store = new AgentPhotoStore(properties);

        var stored = store.storeOriginal("photos/original/photo-1.jpg", new ByteArrayInputStream("photo".getBytes()));

        assertEquals("photos/original/photo-1.jpg", stored.logicalRef());
        assertTrue(Files.exists(tempDir.resolve("office-share/photos/original/photo-1.jpg")));
    }

    @Test
    void storesOriginalThroughS3CompatibleProfile() throws Exception {
        var properties = new ArchDoxAgentProperties();
        var original = new StorageTarget();
        original.setKind("S3_COMPATIBLE");
        original.setBucket("archdox-agent-originals");
        original.setPrefix("office-3");
        properties.getStorage().setOriginal(original);
        properties.getStorage().getS3Compatible().setAccessKey("access");
        properties.getStorage().getS3Compatible().setSecretKey("secret");
        var gateway = new FakeS3ObjectGateway();
        var store = new AgentPhotoStore(properties, new AgentStorageServiceFactory(properties, gateway));

        var stored = store.storeOriginal("photos/original/photo-1.jpg", new ByteArrayInputStream("photo".getBytes()));

        assertEquals("photos/original/photo-1.jpg", stored.logicalRef());
        assertEquals(5, stored.bytes());
        assertEquals("photo", new String(gateway.objects.get("archdox-agent-originals/office-3/photos/original/photo-1.jpg")));
    }

    private static class FakeS3ObjectGateway implements AgentS3ObjectGateway {
        private final Map<String, byte[]> objects = new LinkedHashMap<>();

        @Override
        public void put(
                ArchDoxAgentProperties.S3Compatible connection,
                String bucket,
                String objectKey,
                String contentType,
                Path file,
                long size
        ) throws IOException {
            objects.put(bucket + "/" + objectKey, Files.readAllBytes(file));
        }

        @Override
        public Optional<byte[]> readIfExists(
                ArchDoxAgentProperties.S3Compatible connection,
                String bucket,
                String objectKey
        ) {
            return Optional.ofNullable(objects.get(bucket + "/" + objectKey));
        }

        @Override
        public byte[] read(
                ArchDoxAgentProperties.S3Compatible connection,
                String bucket,
                String objectKey
        ) {
            return objects.get(bucket + "/" + objectKey);
        }

        @Override
        public long size(
                ArchDoxAgentProperties.S3Compatible connection,
                String bucket,
                String objectKey
        ) {
            return objects.get(bucket + "/" + objectKey).length;
        }

        @Override
        public void deleteIfExists(
                ArchDoxAgentProperties.S3Compatible connection,
                String bucket,
                String objectKey
        ) {
            objects.remove(bucket + "/" + objectKey);
        }
    }
}

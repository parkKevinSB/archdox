package com.archdox.agent.photo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
}

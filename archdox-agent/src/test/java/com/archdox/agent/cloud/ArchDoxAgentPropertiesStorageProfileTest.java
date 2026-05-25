package com.archdox.agent.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archdox.agent.cloud.ArchDoxAgentProperties.StorageTarget;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchDoxAgentPropertiesStorageProfileTest {
    @Test
    @SuppressWarnings("unchecked")
    void publicStorageProfileDoesNotExposeAbsoluteLocalPaths(@TempDir Path tempDir) {
        var properties = new ArchDoxAgentProperties();
        properties.setLocalStorageRoot(tempDir.toString());
        var original = new StorageTarget();
        original.setKind("LOCAL_FS");
        original.setRootPath("D:/ArchDoxStorage/originals");
        properties.getStorage().setOriginal(original);

        var profile = properties.storageProfile();
        var originalProfile = (Map<String, Object>) profile.get("original");

        assertEquals("LOCAL_FILE", originalProfile.get("kind"));
        assertEquals(true, originalProfile.get("fileSystemBacked"));
        assertEquals(true, originalProfile.get("rootConfigured"));
        assertFalse(originalProfile.containsKey("rootPath"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void s3CompatibleProfilePublishesLogicalBucketAndPrefixOnly() {
        var properties = new ArchDoxAgentProperties();
        var artifact = new StorageTarget();
        artifact.setKind("MINIO");
        artifact.setBucket("archdox-artifacts");
        artifact.setPrefix("office-3");
        properties.getStorage().setArtifact(artifact);

        var profile = properties.storageProfile();
        var artifactProfile = (Map<String, Object>) profile.get("artifact");

        assertEquals("S3_COMPATIBLE", artifactProfile.get("kind"));
        assertEquals(false, artifactProfile.get("fileSystemBacked"));
        assertEquals(true, artifactProfile.get("bucketConfigured"));
        assertEquals("archdox-artifacts", artifactProfile.get("bucket"));
        assertEquals("office-3", artifactProfile.get("prefix"));
        assertTrue(artifactProfile.containsKey("rootConfigured"));
        assertFalse(artifactProfile.containsKey("rootPath"));
    }
}

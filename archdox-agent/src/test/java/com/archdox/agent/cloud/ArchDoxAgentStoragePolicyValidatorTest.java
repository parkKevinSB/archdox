package com.archdox.agent.cloud;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.archdox.agent.cloud.ArchDoxAgentProperties.StorageTarget;
import org.junit.jupiter.api.Test;

class ArchDoxAgentStoragePolicyValidatorTest {
    @Test
    void cloudManagedAgentRejectsDefaultLocalStorage() {
        var properties = new ArchDoxAgentProperties();
        properties.setDeploymentMode("CLOUD_MANAGED");

        assertThrows(IllegalStateException.class,
                () -> new ArchDoxAgentStoragePolicyValidator(properties).validate());
    }

    @Test
    void cloudManagedAgentRequiresS3Credentials() {
        var properties = cloudManagedS3Properties();
        properties.getStorage().getS3Compatible().setAccessKey("");

        assertThrows(IllegalStateException.class,
                () -> new ArchDoxAgentStoragePolicyValidator(properties).validate());
    }

    @Test
    void cloudManagedAgentAllowsAllS3CompatibleStorageTargets() {
        var properties = cloudManagedS3Properties();

        assertDoesNotThrow(() -> new ArchDoxAgentStoragePolicyValidator(properties).validate());
    }

    @Test
    void localOfficeAgentMayUseLocalStorage() {
        var properties = new ArchDoxAgentProperties();
        properties.setDeploymentMode("LOCAL_OFFICE");

        assertDoesNotThrow(() -> new ArchDoxAgentStoragePolicyValidator(properties).validate());
    }

    private ArchDoxAgentProperties cloudManagedS3Properties() {
        var properties = new ArchDoxAgentProperties();
        properties.setDeploymentMode("CLOUD_MANAGED");
        properties.getStorage().setOriginal(s3Target("originals"));
        properties.getStorage().setWorking(s3Target("working"));
        properties.getStorage().setArtifact(s3Target("artifacts"));
        properties.getStorage().setTemplate(s3Target("templates"));
        properties.getStorage().getS3Compatible().setAccessKey("access-key");
        properties.getStorage().getS3Compatible().setSecretKey("secret-key");
        return properties;
    }

    private StorageTarget s3Target(String prefix) {
        var target = new StorageTarget();
        target.setKind("S3_COMPATIBLE");
        target.setBucket("archdox-agent-storage");
        target.setPrefix(prefix);
        return target;
    }
}

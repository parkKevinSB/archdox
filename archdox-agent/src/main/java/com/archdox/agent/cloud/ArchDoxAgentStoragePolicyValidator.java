package com.archdox.agent.cloud;

import com.archdox.agent.storage.AgentStorageKind;
import com.archdox.agent.storage.AgentStorageTargetProfile;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ArchDoxAgentStoragePolicyValidator implements ApplicationRunner {
    private final ArchDoxAgentProperties properties;

    public ArchDoxAgentStoragePolicyValidator(ArchDoxAgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    void validate() {
        if (!isCloudManaged()) {
            return;
        }

        requireS3Compatible("original photo", properties.originalStorageProfile());
        requireS3Compatible("working photo", properties.workingStorageProfile());
        requireS3Compatible("document artifact", properties.artifactStorageProfile());
        requireS3Compatible("template cache", properties.templateStorageProfile());
        requireS3Connection();
    }

    private boolean isCloudManaged() {
        return "CLOUD_MANAGED".equalsIgnoreCase(properties.getDeploymentMode());
    }

    private void requireS3Compatible(String usage, AgentStorageTargetProfile profile) {
        if (profile.kind() != AgentStorageKind.S3_COMPATIBLE) {
            throw new IllegalStateException(
                    "CLOUD_MANAGED ArchDox Agent requires S3_COMPATIBLE " + usage
                            + " storage; configured kind is " + profile.kind());
        }
        if (!hasText(profile.bucket())) {
            throw new IllegalStateException(
                    "CLOUD_MANAGED ArchDox Agent requires an S3 bucket for " + usage + " storage");
        }
    }

    private void requireS3Connection() {
        var s3 = properties.getStorage().getS3Compatible();
        if (!hasText(s3.getAccessKey()) || !hasText(s3.getSecretKey())) {
            throw new IllegalStateException(
                    "CLOUD_MANAGED ArchDox Agent requires S3-compatible access key and secret key");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

package com.archdox.agent.storage;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import org.springframework.stereotype.Component;

@Component
public class AgentStorageServiceFactory {
    private final ArchDoxAgentProperties properties;
    private final AgentS3ObjectGateway s3Gateway;

    public AgentStorageServiceFactory(ArchDoxAgentProperties properties, AgentS3ObjectGateway s3Gateway) {
        this.properties = properties;
        this.s3Gateway = s3Gateway;
    }

    public AgentStorageService originalPhotos() {
        return forProfile("original photo", properties.originalStorageProfile());
    }

    public AgentStorageService workingPhotos() {
        return forProfile("working photo", properties.workingStorageProfile());
    }

    public AgentStorageService documentArtifacts() {
        return forProfile("document artifact", properties.artifactStorageProfile());
    }

    public AgentStorageService templateCache() {
        return forProfile("template cache", properties.templateStorageProfile());
    }

    public AgentStorageService forProfile(String usage, AgentStorageTargetProfile profile) {
        return switch (profile.kind()) {
            case LOCAL_FILE, NAS -> new AgentFileSystemStorageService(usage, profile);
            case S3_COMPATIBLE -> new AgentS3CompatibleStorageService(
                    usage,
                    profile,
                    properties.getStorage().getS3Compatible(),
                    s3Gateway);
        };
    }
}

package com.archdox.agent.photo;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import com.archdox.agent.storage.AgentStorageService;
import com.archdox.agent.storage.AgentStorageServiceFactory;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentPhotoStore {
    private final AgentStorageService storage;

    @Autowired
    public AgentPhotoStore(ArchDoxAgentProperties properties, AgentStorageServiceFactory storageServiceFactory) {
        this.storage = storageServiceFactory.originalPhotos();
    }

    public AgentPhotoStore(ArchDoxAgentProperties properties) {
        this.storage = new AgentStorageServiceFactory(
                properties,
                new com.archdox.agent.storage.S3CompatibleAgentObjectGateway()).originalPhotos();
    }

    AgentPhotoStore(AgentStorageService storage) {
        this.storage = storage;
    }

    public StoredPhoto storeOriginal(String logicalRef, InputStream input) throws IOException {
        var stored = storage.put(logicalRef, input, "application/octet-stream");
        return new StoredPhoto(stored.logicalRef(), stored.storageLocation(), stored.bytes());
    }

    public void deleteIfExists(String logicalRef) throws IOException {
        storage.deleteIfExists(logicalRef);
    }

    public record StoredPhoto(String logicalRef, String storageLocation, long bytes) {
    }
}

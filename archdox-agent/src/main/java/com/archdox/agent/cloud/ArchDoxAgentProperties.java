package com.archdox.agent.cloud;

import com.archdox.agent.storage.AgentStorageTargetProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.agent")
public class ArchDoxAgentProperties {
    private String cloudWsUrl = "ws://localhost:8080/agent/ws";
    private String cloudHttpBaseUrl = "http://localhost:8080";
    private boolean enabled;
    private String authMode;
    private Long agentId;
    private Long officeId = 0L;
    private String agentCode = "local-dev";
    private String deploymentMode = "LOCAL_OFFICE";
    private String token = "dev-agent-secret-change-me";
    private String installToken;
    private String deviceSecret;
    private String version = "0.0.1-dev";
    private String protocolVersion = "2026-06-25";
    private String launcherVersion = "embedded";
    private String updateChannel = "stable";
    private long heartbeatIntervalMs = 30000;
    private long reconnectIntervalMs = 5000;
    private int websocketMaxTextMessageBufferBytes = 2 * 1024 * 1024;
    private int websocketMaxBinaryMessageBufferBytes = 2 * 1024 * 1024;
    private String localStorageRoot = "build/archdox-agent-storage";
    private Storage storage = new Storage();
    private Execution execution = new Execution();

    public String getCloudWsUrl() {
        return cloudWsUrl;
    }

    public void setCloudWsUrl(String cloudWsUrl) {
        this.cloudWsUrl = cloudWsUrl;
    }

    public String getCloudHttpBaseUrl() {
        return cloudHttpBaseUrl;
    }

    public void setCloudHttpBaseUrl(String cloudHttpBaseUrl) {
        this.cloudHttpBaseUrl = cloudHttpBaseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAuthMode() {
        if (authMode != null && !authMode.isBlank()) {
            return authMode;
        }
        if (agentId != null && deviceSecret != null && !deviceSecret.isBlank()) {
            return "DEVICE_SECRET";
        }
        if (installToken != null && !installToken.isBlank()) {
            return "INSTALL_TOKEN";
        }
        return "SHARED_SECRET";
    }

    public void setAuthMode(String authMode) {
        this.authMode = authMode;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getOfficeId() {
        return officeId;
    }

    public void setOfficeId(Long officeId) {
        this.officeId = officeId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getDeploymentMode() {
        return deploymentMode;
    }

    public void setDeploymentMode(String deploymentMode) {
        this.deploymentMode = deploymentMode;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getInstallToken() {
        return installToken;
    }

    public void setInstallToken(String installToken) {
        this.installToken = installToken;
    }

    public String getDeviceSecret() {
        return deviceSecret;
    }

    public void setDeviceSecret(String deviceSecret) {
        this.deviceSecret = deviceSecret;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getLauncherVersion() {
        return launcherVersion;
    }

    public void setLauncherVersion(String launcherVersion) {
        this.launcherVersion = launcherVersion;
    }

    public String getUpdateChannel() {
        return updateChannel;
    }

    public void setUpdateChannel(String updateChannel) {
        this.updateChannel = updateChannel;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public long getReconnectIntervalMs() {
        return reconnectIntervalMs;
    }

    public void setReconnectIntervalMs(long reconnectIntervalMs) {
        this.reconnectIntervalMs = reconnectIntervalMs;
    }

    public int getWebsocketMaxTextMessageBufferBytes() {
        return websocketMaxTextMessageBufferBytes;
    }

    public void setWebsocketMaxTextMessageBufferBytes(int websocketMaxTextMessageBufferBytes) {
        this.websocketMaxTextMessageBufferBytes = websocketMaxTextMessageBufferBytes;
    }

    public int getWebsocketMaxBinaryMessageBufferBytes() {
        return websocketMaxBinaryMessageBufferBytes;
    }

    public void setWebsocketMaxBinaryMessageBufferBytes(int websocketMaxBinaryMessageBufferBytes) {
        this.websocketMaxBinaryMessageBufferBytes = websocketMaxBinaryMessageBufferBytes;
    }

    public int safeWebsocketMaxTextMessageBufferBytes() {
        return Math.max(64 * 1024, websocketMaxTextMessageBufferBytes);
    }

    public int safeWebsocketMaxBinaryMessageBufferBytes() {
        return Math.max(64 * 1024, websocketMaxBinaryMessageBufferBytes);
    }

    public String getLocalStorageRoot() {
        return localStorageRoot;
    }

    public void setLocalStorageRoot(String localStorageRoot) {
        this.localStorageRoot = localStorageRoot;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage == null ? new Storage() : storage;
    }

    public Execution getExecution() {
        return execution;
    }

    public void setExecution(Execution execution) {
        this.execution = execution == null ? new Execution() : execution;
    }

    public String originalRootPath() {
        return originalStorageProfile().rootPath();
    }

    public String artifactRootPath() {
        return artifactStorageProfile().rootPath();
    }

    public String workingRootPath() {
        return workingStorageProfile().rootPath();
    }

    public String templateRootPath() {
        return templateStorageProfile().rootPath();
    }

    public AgentStorageTargetProfile originalStorageProfile() {
        return AgentStorageTargetProfile.from(storage.original, localStorageRoot);
    }

    public AgentStorageTargetProfile workingStorageProfile() {
        return AgentStorageTargetProfile.from(storage.working, localStorageRoot);
    }

    public AgentStorageTargetProfile artifactStorageProfile() {
        return AgentStorageTargetProfile.from(storage.artifact, localStorageRoot);
    }

    public AgentStorageTargetProfile templateStorageProfile() {
        return AgentStorageTargetProfile.from(storage.template, localStorageRoot);
    }

    public Map<String, Object> storageProfile() {
        var profile = new LinkedHashMap<String, Object>();
        profile.put("original", originalStorageProfile().publicProfile());
        profile.put("working", workingStorageProfile().publicProfile());
        profile.put("artifact", artifactStorageProfile().publicProfile());
        profile.put("template", templateStorageProfile().publicProfile());
        return profile;
    }

    public static class Storage {
        private StorageTarget original = new StorageTarget();
        private StorageTarget working = new StorageTarget();
        private StorageTarget artifact = new StorageTarget();
        private StorageTarget template = new StorageTarget();
        private S3Compatible s3Compatible = new S3Compatible();

        public StorageTarget getOriginal() {
            return original;
        }

        public void setOriginal(StorageTarget original) {
            this.original = original == null ? new StorageTarget() : original;
        }

        public StorageTarget getWorking() {
            return working;
        }

        public void setWorking(StorageTarget working) {
            this.working = working == null ? new StorageTarget() : working;
        }

        public StorageTarget getArtifact() {
            return artifact;
        }

        public void setArtifact(StorageTarget artifact) {
            this.artifact = artifact == null ? new StorageTarget() : artifact;
        }

        public StorageTarget getTemplate() {
            return template;
        }

        public void setTemplate(StorageTarget template) {
            this.template = template == null ? new StorageTarget() : template;
        }

        public S3Compatible getS3Compatible() {
            return s3Compatible;
        }

        public void setS3Compatible(S3Compatible s3Compatible) {
            this.s3Compatible = s3Compatible == null ? new S3Compatible() : s3Compatible;
        }
    }

    public static class Execution {
        private int documentRenderConcurrency = 2;
        private int documentRenderQueueCapacity = 50;
        private int photoPickupConcurrency = 4;
        private int photoPickupQueueCapacity = 100;
        private int artifactDeliveryConcurrency = 4;
        private int artifactDeliveryQueueCapacity = 100;

        public int getDocumentRenderConcurrency() {
            return documentRenderConcurrency;
        }

        public void setDocumentRenderConcurrency(int documentRenderConcurrency) {
            this.documentRenderConcurrency = documentRenderConcurrency;
        }

        public int getDocumentRenderQueueCapacity() {
            return documentRenderQueueCapacity;
        }

        public void setDocumentRenderQueueCapacity(int documentRenderQueueCapacity) {
            this.documentRenderQueueCapacity = documentRenderQueueCapacity;
        }

        public int getPhotoPickupConcurrency() {
            return photoPickupConcurrency;
        }

        public void setPhotoPickupConcurrency(int photoPickupConcurrency) {
            this.photoPickupConcurrency = photoPickupConcurrency;
        }

        public int getPhotoPickupQueueCapacity() {
            return photoPickupQueueCapacity;
        }

        public void setPhotoPickupQueueCapacity(int photoPickupQueueCapacity) {
            this.photoPickupQueueCapacity = photoPickupQueueCapacity;
        }

        public int getArtifactDeliveryConcurrency() {
            return artifactDeliveryConcurrency;
        }

        public void setArtifactDeliveryConcurrency(int artifactDeliveryConcurrency) {
            this.artifactDeliveryConcurrency = artifactDeliveryConcurrency;
        }

        public int getArtifactDeliveryQueueCapacity() {
            return artifactDeliveryQueueCapacity;
        }

        public void setArtifactDeliveryQueueCapacity(int artifactDeliveryQueueCapacity) {
            this.artifactDeliveryQueueCapacity = artifactDeliveryQueueCapacity;
        }

        public int safeDocumentRenderConcurrency() {
            return Math.max(1, documentRenderConcurrency);
        }

        public int safeDocumentRenderQueueCapacity() {
            return Math.max(0, documentRenderQueueCapacity);
        }

        public int safePhotoPickupConcurrency() {
            return Math.max(1, photoPickupConcurrency);
        }

        public int safePhotoPickupQueueCapacity() {
            return Math.max(0, photoPickupQueueCapacity);
        }

        public int safeArtifactDeliveryConcurrency() {
            return Math.max(1, artifactDeliveryConcurrency);
        }

        public int safeArtifactDeliveryQueueCapacity() {
            return Math.max(0, artifactDeliveryQueueCapacity);
        }
    }

    public static class StorageTarget {
        private String kind = "LOCAL_FILE";
        private String rootPath;
        private String bucket;
        private String prefix;

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getRootPath() {
            return rootPath;
        }

        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

    }

    public static class S3Compatible {
        private String endpoint;
        private String region = "ap-northeast-2";
        private String accessKey;
        private String secretKey;
        private boolean pathStyleAccess = true;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public boolean isPathStyleAccess() {
            return pathStyleAccess;
        }

        public void setPathStyleAccess(boolean pathStyleAccess) {
            this.pathStyleAccess = pathStyleAccess;
        }
    }
}

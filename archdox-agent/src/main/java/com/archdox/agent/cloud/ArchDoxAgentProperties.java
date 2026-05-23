package com.archdox.agent.cloud;

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
    private long heartbeatIntervalMs = 30000;
    private String localStorageRoot = "build/archdox-agent-storage";
    private Storage storage = new Storage();

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

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
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

    public String originalRootPath() {
        return storage.original.rootPath == null ? localStorageRoot : storage.original.rootPath;
    }

    public String artifactRootPath() {
        return storage.artifact.rootPath == null ? localStorageRoot : storage.artifact.rootPath;
    }

    public Map<String, Object> storageProfile() {
        var profile = new LinkedHashMap<String, Object>();
        profile.put("original", storage.original.asMap(localStorageRoot));
        profile.put("working", storage.working.asMap(localStorageRoot));
        profile.put("artifact", storage.artifact.asMap(localStorageRoot));
        return profile;
    }

    public static class Storage {
        private StorageTarget original = new StorageTarget();
        private StorageTarget working = new StorageTarget();
        private StorageTarget artifact = new StorageTarget();

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
    }

    public static class StorageTarget {
        private String kind = "LOCAL_FS";
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

        Map<String, Object> asMap(String fallbackRootPath) {
            var value = new LinkedHashMap<String, Object>();
            value.put("kind", kind == null || kind.isBlank() ? "LOCAL_FS" : kind);
            value.put("rootPath", rootPath == null || rootPath.isBlank() ? fallbackRootPath : rootPath);
            if (bucket != null && !bucket.isBlank()) {
                value.put("bucket", bucket);
            }
            if (prefix != null && !prefix.isBlank()) {
                value.put("prefix", prefix);
            }
            return value;
        }
    }
}

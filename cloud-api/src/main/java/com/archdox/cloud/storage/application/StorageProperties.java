package com.archdox.cloud.storage.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.storage")
public class StorageProperties {
    private StorageType active = StorageType.LOCAL_FILE;
    private final LocalFile localFile = new LocalFile();
    private final Nas nas = new Nas();
    private final S3Compatible s3Compatible = new S3Compatible();

    public StorageType getActive() {
        return active;
    }

    public void setActive(StorageType active) {
        this.active = active == null ? StorageType.LOCAL_FILE : active;
    }

    public LocalFile getLocalFile() {
        return localFile;
    }

    public Nas getNas() {
        return nas;
    }

    public S3Compatible getS3Compatible() {
        return s3Compatible;
    }

    public static class LocalFile {
        private String rootPath = "build/storage";
        private String bucketName = "local";

        public String getRootPath() {
            return rootPath;
        }

        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }
    }

    public static class Nas {
        private String rootPath = "build/nas-storage";
        private String bucketName = "nas";

        public String getRootPath() {
            return rootPath;
        }

        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }
    }

    public static class S3Compatible {
        private String endpoint;
        private String region = "ap-northeast-2";
        private String bucketName;
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

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
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

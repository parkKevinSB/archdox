package com.archdox.cloud.photo.application;

import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.photos.storage")
public class PhotoStorageProperties {
    private PhotoUploadTarget uploadTarget = PhotoUploadTarget.API_LOCAL;
    private String localRoot = "build/photo-storage";
    private final S3 s3 = new S3();

    public PhotoUploadTarget getUploadTarget() {
        return uploadTarget;
    }

    public void setUploadTarget(PhotoUploadTarget uploadTarget) {
        this.uploadTarget = uploadTarget;
    }

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public S3 getS3() {
        return s3;
    }

    public static class S3 {
        private String endpoint;
        private String region = "ap-northeast-2";
        private String bucket;
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

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
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

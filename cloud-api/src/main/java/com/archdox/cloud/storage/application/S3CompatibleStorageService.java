package com.archdox.cloud.storage.application;

import com.archdox.cloud.global.api.BadRequestException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
public class S3CompatibleStorageService implements StorageService {
    private final StorageProperties properties;

    public S3CompatibleStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StorageType storageType() {
        return StorageType.S3_COMPATIBLE;
    }

    @Override
    public StorageObjectRef put(
            String objectKey,
            String originalFileName,
            String contentType,
            long size,
            InputStream input
    ) {
        validateConfigured();
        try (var s3 = s3Client()) {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(s3().getBucketName())
                            .key(objectKey)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromInputStream(input, size));
            return new StorageObjectRef(
                    UUID.randomUUID().toString(),
                    StorageType.S3_COMPATIBLE,
                    s3().getBucketName(),
                    objectKey,
                    originalFileName,
                    contentType,
                    size);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        validateConfigured();
        var s3 = s3Client();
        var input = s3.getObject(GetObjectRequest.builder()
                .bucket(s3().getBucketName())
                .key(objectKey)
                .build());
        return new FilterInputStream(input) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    s3.close();
                }
            }
        };
    }

    @Override
    public void deleteIfExists(String objectKey) {
        validateConfigured();
        try (var s3 = s3Client()) {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3().getBucketName())
                    .key(objectKey)
                    .build());
        }
    }

    @Override
    public StorageUploadUrl createPresignedPutUrl(String objectKey, String contentType, OffsetDateTime expiresAt) {
        validateConfigured();
        var ttl = Duration.between(OffsetDateTime.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofMinutes(1);
        }
        try (var presigner = presigner()) {
            var request = PutObjectRequest.builder()
                    .bucket(s3().getBucketName())
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .putObjectRequest(request)
                    .build());
            return new StorageUploadUrl("PUT", presigned.url().toString(), Map.of("Content-Type", contentType), expiresAt);
        }
    }

    private S3Presigner presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(s3().getRegion()))
                .credentialsProvider(credentials())
                .serviceConfiguration(s3Configuration());
        if (hasText(s3().getEndpoint())) {
            builder.endpointOverride(URI.create(s3().getEndpoint()));
        }
        return builder.build();
    }

    private S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(s3().getRegion()))
                .credentialsProvider(credentials())
                .serviceConfiguration(s3Configuration());
        if (hasText(s3().getEndpoint())) {
            builder.endpointOverride(URI.create(s3().getEndpoint()));
        }
        return builder.build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(s3().getAccessKey(), s3().getSecretKey()));
    }

    private S3Configuration s3Configuration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(s3().isPathStyleAccess())
                .build();
    }

    private StorageProperties.S3Compatible s3() {
        return properties.getS3Compatible();
    }

    private void validateConfigured() {
        if (!hasText(s3().getBucketName()) || !hasText(s3().getAccessKey()) || !hasText(s3().getSecretKey())) {
            throw new BadRequestException("S3-compatible storage is not configured");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

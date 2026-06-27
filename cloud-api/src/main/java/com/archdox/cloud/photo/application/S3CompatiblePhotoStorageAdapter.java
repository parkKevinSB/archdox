package com.archdox.cloud.photo.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAsset;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.domain.PhotoUploadKind;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import com.archdox.cloud.photo.dto.PhotoUploadInstructionResponse;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
public class S3CompatiblePhotoStorageAdapter implements PhotoStorageAdapter {
    private final PhotoStorageProperties properties;

    public S3CompatiblePhotoStorageAdapter(PhotoStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(PhotoUploadTarget target) {
        return target == PhotoUploadTarget.S3 || target == PhotoUploadTarget.CLOUD_MEDIATED;
    }

    @Override
    public boolean supports(PhotoStorageKind storageKind) {
        return storageKind == PhotoStorageKind.S3 || storageKind == PhotoStorageKind.S3_TEMP;
    }

    @Override
    public PhotoStorageKind storageKindFor(PhotoUploadTarget target, PhotoAssetType assetType) {
        if (target == PhotoUploadTarget.CLOUD_MEDIATED && assetType == PhotoAssetType.ORIGINAL) {
            return PhotoStorageKind.S3_TEMP;
        }
        return PhotoStorageKind.S3;
    }

    @Override
    public List<PhotoUploadInstructionResponse> createUploadInstructions(
            Photo photo,
            List<PhotoAsset> assets,
            OffsetDateTime expiresAt
    ) {
        validateConfigured();
        var ttl = Duration.between(OffsetDateTime.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofMinutes(1);
        }
        try (var presigner = presigner()) {
            Duration signatureTtl = ttl;
            return assets.stream()
                    .map(asset -> uploadInstruction(presigner, photo, asset, signatureTtl, expiresAt))
                    .toList();
        }
    }

    @Override
    public void deleteIfExists(String storageRef) {
        validateConfigured();
        try (var s3 = s3Client()) {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getS3().getBucket())
                    .key(storageRef)
                    .build());
        }
    }

    @Override
    public void storeContent(PhotoAsset asset, Long contentLength, InputStream input) throws IOException {
        validateConfigured();
        if (contentLength == null || contentLength < 0) {
            throw new IOException("S3 content length is required");
        }
        try (var s3 = s3Client()) {
            var putRequest = PutObjectRequest.builder()
                    .bucket(properties.getS3().getBucket())
                    .key(asset.storageRef())
                    .contentType(asset.mimeType())
                    .build();
            s3.putObject(putRequest, RequestBody.fromInputStream(input, contentLength));
        }
    }

    @Override
    public InputStream openContent(PhotoAsset asset) throws IOException {
        validateConfigured();
        var s3 = s3Client();
        var getRequest = GetObjectRequest.builder()
                .bucket(properties.getS3().getBucket())
                .key(asset.storageRef())
                .build();
        InputStream input;
        try {
            input = s3.getObject(getRequest);
        } catch (NoSuchKeyException ex) {
            s3.close();
            throw new FileNotFoundException("S3 photo asset not found: " + asset.storageRef());
        }
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
    public PhotoDownloadInstruction createDownloadInstruction(PhotoAsset asset, OffsetDateTime expiresAt) {
        validateConfigured();
        var ttl = Duration.between(OffsetDateTime.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofMinutes(1);
        }
        try (var presigner = presigner()) {
            var getRequest = GetObjectRequest.builder()
                    .bucket(properties.getS3().getBucket())
                    .key(asset.storageRef())
                    .build();
            var presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(getRequest)
                    .build();
            var presigned = presigner.presignGetObject(presignRequest);
            return new PhotoDownloadInstruction(
                    "GET",
                    presigned.url().toString(),
                    Map.of(),
                    expiresAt);
        }
    }

    private PhotoUploadInstructionResponse presignedPut(
            S3Presigner presigner,
            PhotoAsset asset,
            Duration ttl,
            OffsetDateTime expiresAt
    ) {
        var putRequest = PutObjectRequest.builder()
                .bucket(properties.getS3().getBucket())
                .key(asset.storageRef())
                .contentType(asset.mimeType())
                .build();
        var presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(putRequest)
                .build();
        var presigned = presigner.presignPutObject(presignRequest);
        return new PhotoUploadInstructionResponse(
                uploadKind(asset.assetType()),
                "PUT",
                presigned.url().toString(),
                Map.of(),
                Map.of("Content-Type", asset.mimeType()),
                null,
                expiresAt);
    }

    private PhotoUploadInstructionResponse uploadInstruction(
            S3Presigner presigner,
            Photo photo,
            PhotoAsset asset,
            Duration ttl,
            OffsetDateTime expiresAt
    ) {
        if (asset.storageKind() == PhotoStorageKind.S3_TEMP && asset.assetType() == PhotoAssetType.ORIGINAL) {
            return apiMediatedPut(photo, asset, expiresAt);
        }
        return presignedPut(presigner, asset, ttl, expiresAt);
    }

    private PhotoUploadInstructionResponse apiMediatedPut(
            Photo photo,
            PhotoAsset asset,
            OffsetDateTime expiresAt
    ) {
        if (photo == null || photo.id() == null) {
            throw new IllegalArgumentException("Photo id is required for mediated S3 upload");
        }
        return new PhotoUploadInstructionResponse(
                uploadKind(asset.assetType()),
                "PUT",
                "/api/v1/photos/%d/content/%s".formatted(photo.id(), uploadKind(asset.assetType())),
                Map.of(),
                Map.of(),
                null,
                expiresAt);
    }

    private S3Presigner presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.getS3().getRegion()))
                .credentialsProvider(credentials())
                .serviceConfiguration(s3Configuration());
        if (hasText(properties.getS3().getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getS3().getEndpoint()));
        }
        return builder.build();
    }

    private S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(properties.getS3().getRegion()))
                .credentialsProvider(credentials())
                .serviceConfiguration(s3Configuration());
        if (hasText(properties.getS3().getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getS3().getEndpoint()));
        }
        return builder.build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.getS3().getAccessKey(),
                properties.getS3().getSecretKey()));
    }

    private S3Configuration s3Configuration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(properties.getS3().isPathStyleAccess())
                .build();
    }

    private void validateConfigured() {
        var s3 = properties.getS3();
        if (!hasText(s3.getBucket()) || !hasText(s3.getAccessKey()) || !hasText(s3.getSecretKey())) {
            throw new BadRequestException("S3-compatible photo storage is not configured");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private PhotoUploadKind uploadKind(PhotoAssetType assetType) {
        return PhotoUploadKind.valueOf(assetType.name().toUpperCase(Locale.ROOT));
    }
}

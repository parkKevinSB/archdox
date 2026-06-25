package com.archdox.cloud.officestorage.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.officestorage.domain.OfficeStorageConnectionTestStatus;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class OfficeStorageConnectionTester {
    public OfficeStorageConnectionTestResult test(OfficeStorageConnectionProfile profile) {
        validate(profile);
        var started = System.nanoTime();
        var key = testObjectKey(profile.objectPrefix());
        var content = "archdox office storage connection test " + UUID.randomUUID();
        try (var s3 = s3Client(profile)) {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(profile.bucketName())
                            .key(key)
                            .contentType("text/plain; charset=utf-8")
                            .build(),
                    RequestBody.fromString(content, StandardCharsets.UTF_8));
            ResponseBytes<GetObjectResponse> read = s3.getObject(
                    GetObjectRequest.builder()
                            .bucket(profile.bucketName())
                            .key(key)
                            .build(),
                    ResponseTransformer.toBytes());
            if (!content.equals(read.asUtf8String())) {
                return result(OfficeStorageConnectionTestStatus.FAILED, "Test object readback did not match.", started);
            }
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(profile.bucketName())
                    .key(key)
                    .build());
            return result(OfficeStorageConnectionTestStatus.SUCCEEDED, "Connection test succeeded.", started);
        } catch (S3Exception ex) {
            return result(
                    OfficeStorageConnectionTestStatus.FAILED,
                    "S3 test failed: " + safeMessage(ex.awsErrorDetails() == null ? ex.getMessage() : ex.awsErrorDetails().errorMessage()),
                    started);
        } catch (RuntimeException ex) {
            return result(OfficeStorageConnectionTestStatus.FAILED, "S3 test failed: " + safeMessage(ex.getMessage()), started);
        }
    }

    private S3Client s3Client(OfficeStorageConnectionProfile profile) {
        var builder = S3Client.builder()
                .region(Region.of(profile.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(profile.accessKey(), profile.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(profile.pathStyleAccess())
                        .build());
        if (hasText(profile.endpoint())) {
            builder.endpointOverride(URI.create(profile.endpoint().trim()));
        }
        return builder.build();
    }

    private void validate(OfficeStorageConnectionProfile profile) {
        if (!hasText(profile.region())) {
            throw new BadRequestException("S3 region is required");
        }
        if (!hasText(profile.bucketName())) {
            throw new BadRequestException("S3 bucket is required");
        }
        if (!hasText(profile.accessKey()) || !hasText(profile.secretKey())) {
            throw new BadRequestException("S3 access key and secret key are required");
        }
        if (profile.providerType() != null && profile.providerType().name().contains("MINIO") && !hasText(profile.endpoint())) {
            throw new BadRequestException("S3-compatible endpoint is required for MinIO");
        }
    }

    private OfficeStorageConnectionTestResult result(
            OfficeStorageConnectionTestStatus status,
            String message,
            long started
    ) {
        var elapsedMs = Math.max(1, (System.nanoTime() - started) / 1_000_000);
        return new OfficeStorageConnectionTestResult(status, message, elapsedMs);
    }

    private String testObjectKey(String prefix) {
        var normalizedPrefix = normalizePrefix(prefix);
        var key = "archdox-connection-test/" + UUID.randomUUID() + ".txt";
        return normalizedPrefix == null ? key : normalizedPrefix + "/" + key;
    }

    private String normalizePrefix(String prefix) {
        if (!hasText(prefix)) {
            return null;
        }
        var normalized = prefix.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private String safeMessage(String message) {
        if (!hasText(message)) {
            return "unknown error";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

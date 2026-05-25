package com.archdox.agent.storage;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class S3CompatibleAgentObjectGateway implements AgentS3ObjectGateway {
    @Override
    public void put(
            ArchDoxAgentProperties.S3Compatible connection,
            String bucket,
            String objectKey,
            String contentType,
            Path file,
            long size
    ) {
        validateConfigured(connection, bucket);
        try (var s3 = s3Client(connection)) {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType(contentType)
                            .contentLength(size)
                            .build(),
                    RequestBody.fromFile(file));
        }
    }

    @Override
    public Optional<byte[]> readIfExists(
            ArchDoxAgentProperties.S3Compatible connection,
            String bucket,
            String objectKey
    ) throws IOException {
        validateConfigured(connection, bucket);
        try {
            return Optional.of(read(connection, bucket, objectKey));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    @Override
    public byte[] read(
            ArchDoxAgentProperties.S3Compatible connection,
            String bucket,
            String objectKey
    ) throws IOException {
        validateConfigured(connection, bucket);
        try (var s3 = s3Client(connection);
                var input = s3.getObject(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build())) {
            return input.readAllBytes();
        }
    }

    @Override
    public long size(
            ArchDoxAgentProperties.S3Compatible connection,
            String bucket,
            String objectKey
    ) {
        validateConfigured(connection, bucket);
        try (var s3 = s3Client(connection)) {
            return s3.headObject(HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build())
                    .contentLength();
        }
    }

    @Override
    public void deleteIfExists(
            ArchDoxAgentProperties.S3Compatible connection,
            String bucket,
            String objectKey
    ) {
        validateConfigured(connection, bucket);
        try (var s3 = s3Client(connection)) {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        }
    }

    private S3Client s3Client(ArchDoxAgentProperties.S3Compatible connection) {
        var builder = S3Client.builder()
                .region(Region.of(connection.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(connection.getAccessKey(), connection.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(connection.isPathStyleAccess())
                        .build());
        if (hasText(connection.getEndpoint())) {
            builder.endpointOverride(URI.create(connection.getEndpoint()));
        }
        return builder.build();
    }

    private void validateConfigured(ArchDoxAgentProperties.S3Compatible connection, String bucket) {
        if (!hasText(bucket)
                || !hasText(connection.getAccessKey())
                || !hasText(connection.getSecretKey())) {
            throw new IllegalStateException("ArchDox Agent S3-compatible storage is not configured");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

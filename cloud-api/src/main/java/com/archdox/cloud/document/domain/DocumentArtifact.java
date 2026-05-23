package com.archdox.cloud.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "document_artifacts")
public class DocumentArtifact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "document_job_id", nullable = false)
    private Long documentJobId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false)
    private DocumentArtifactType artifactType;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_kind", nullable = false)
    private DocumentArtifactStorageKind storageKind;

    @Column(name = "storage_ref", nullable = false)
    private String storageRef;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private long bytes;

    @Column(name = "hash_sha256", nullable = false)
    private String hashSha256;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected DocumentArtifact() {
    }

    public DocumentArtifact(
            Long officeId,
            Long documentJobId,
            Long reportId,
            DocumentArtifactType artifactType,
            DocumentArtifactStorageKind storageKind,
            String storageRef,
            String fileName,
            String mimeType,
            long bytes,
            String hashSha256,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.documentJobId = documentJobId;
        this.reportId = reportId;
        this.artifactType = artifactType;
        this.storageKind = storageKind;
        this.storageRef = storageRef;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.bytes = bytes;
        this.hashSha256 = hashSha256;
        this.createdAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long documentJobId() {
        return documentJobId;
    }

    public Long reportId() {
        return reportId;
    }

    public DocumentArtifactType artifactType() {
        return artifactType;
    }

    public DocumentArtifactStorageKind storageKind() {
        return storageKind;
    }

    public String storageRef() {
        return storageRef;
    }

    public String fileName() {
        return fileName;
    }

    public String mimeType() {
        return mimeType;
    }

    public long bytes() {
        return bytes;
    }

    public String hashSha256() {
        return hashSha256;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }
}

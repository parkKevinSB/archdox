package com.archdox.cloud.photo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "photo_assets")
public class PhotoAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photo_id", nullable = false)
    private Photo photo;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private PhotoAssetType assetType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoAssetStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_kind", nullable = false)
    private PhotoStorageKind storageKind;

    @Column(name = "storage_ref", nullable = false)
    private String storageRef;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    private Long bytes;

    private Integer width;

    private Integer height;

    @Column(name = "hash_sha256")
    private String hashSha256;

    @Column(nullable = false)
    private boolean temporary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;

    @Column(name = "picked_up_at")
    private OffsetDateTime pickedUpAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected PhotoAsset() {
    }

    public PhotoAsset(
            Photo photo,
            PhotoAssetType assetType,
            PhotoStorageKind storageKind,
            String storageRef,
            String mimeType,
            Long bytes,
            String hashSha256,
            boolean temporary,
            OffsetDateTime now
    ) {
        this.photo = photo;
        this.assetType = assetType;
        this.status = PhotoAssetStatus.PENDING_UPLOAD;
        this.storageKind = storageKind;
        this.storageRef = storageRef;
        this.mimeType = mimeType;
        this.bytes = bytes;
        this.hashSha256 = hashSha256;
        this.temporary = temporary;
        this.createdAt = now;
    }

    public void markUploaded(Long bytes, OffsetDateTime now) {
        if (bytes != null && bytes > 0) {
            this.bytes = bytes;
        }
        this.status = PhotoAssetStatus.UPLOADED;
        this.uploadedAt = now;
    }

    public void updateImageInfo(Long bytes, Integer width, Integer height, String hashSha256) {
        this.bytes = bytes;
        this.width = width;
        this.height = height;
        this.hashSha256 = hashSha256;
    }

    public void relocateToAgentManaged(String localStorageRef, OffsetDateTime now) {
        this.storageKind = PhotoStorageKind.AGENT_MANAGED;
        this.storageRef = localStorageRef;
        this.temporary = false;
        this.status = PhotoAssetStatus.PICKED_UP;
        this.pickedUpAt = now;
    }

    public Long id() {
        return id;
    }

    public Photo photo() {
        return photo;
    }

    public PhotoAssetType assetType() {
        return assetType;
    }

    public PhotoAssetStatus status() {
        return status;
    }

    public PhotoStorageKind storageKind() {
        return storageKind;
    }

    public String storageRef() {
        return storageRef;
    }

    public String mimeType() {
        return mimeType;
    }

    public Long bytes() {
        return bytes;
    }

    public Integer width() {
        return width;
    }

    public Integer height() {
        return height;
    }

    public String hashSha256() {
        return hashSha256;
    }

    public boolean temporary() {
        return temporary;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime uploadedAt() {
        return uploadedAt;
    }

    public OffsetDateTime pickedUpAt() {
        return pickedUpAt;
    }

    public OffsetDateTime deletedAt() {
        return deletedAt;
    }
}

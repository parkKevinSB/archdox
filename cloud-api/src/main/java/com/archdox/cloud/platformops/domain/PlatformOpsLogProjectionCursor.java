package com.archdox.cloud.platformops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "platform_ops_log_projection_cursors")
public class PlatformOpsLogProjectionCursor {
    @Id
    @Column(name = "source_code", nullable = false, length = 80)
    private String sourceCode;

    @Column(name = "log_path", nullable = false)
    private String logPath;

    @Column(name = "position_bytes", nullable = false)
    private long positionBytes;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "last_scanned_at")
    private OffsetDateTime lastScannedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PlatformOpsLogProjectionCursor() {
    }

    public PlatformOpsLogProjectionCursor(String sourceCode, String logPath, OffsetDateTime now) {
        this.sourceCode = sourceCode;
        this.logPath = logPath;
        this.positionBytes = 0;
        this.fileSizeBytes = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void advance(String logPath, long positionBytes, long fileSizeBytes, OffsetDateTime now) {
        this.logPath = logPath;
        this.positionBytes = Math.max(0, positionBytes);
        this.fileSizeBytes = Math.max(0, fileSizeBytes);
        this.lastScannedAt = now;
        this.updatedAt = now;
    }

    public String sourceCode() {
        return sourceCode;
    }

    public String logPath() {
        return logPath;
    }

    public long positionBytes() {
        return positionBytes;
    }

    public long fileSizeBytes() {
        return fileSizeBytes;
    }

    public OffsetDateTime lastScannedAt() {
        return lastScannedAt;
    }
}

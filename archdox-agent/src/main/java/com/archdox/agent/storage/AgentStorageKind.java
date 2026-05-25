package com.archdox.agent.storage;

import java.util.Locale;

public enum AgentStorageKind {
    LOCAL_FILE(true),
    NAS(true),
    S3_COMPATIBLE(false);

    private final boolean fileSystemBacked;

    AgentStorageKind(boolean fileSystemBacked) {
        this.fileSystemBacked = fileSystemBacked;
    }

    public static AgentStorageKind from(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL_FILE;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "LOCAL_FILE", "LOCAL_FS", "LOCAL" -> LOCAL_FILE;
            case "NAS", "NETWORK_SHARE", "SMB" -> NAS;
            case "S3_COMPATIBLE", "S3", "MINIO", "OBJECT_STORAGE" -> S3_COMPATIBLE;
            default -> throw new IllegalArgumentException("Unsupported ArchDox Agent storage kind: " + value);
        };
    }

    public boolean isFileSystemBacked() {
        return fileSystemBacked;
    }
}

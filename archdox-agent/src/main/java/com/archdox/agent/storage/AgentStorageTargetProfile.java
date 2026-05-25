package com.archdox.agent.storage;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentStorageTargetProfile(
        AgentStorageKind kind,
        String rootPath,
        String bucket,
        String prefix
) {
    public static AgentStorageTargetProfile from(
            ArchDoxAgentProperties.StorageTarget target,
            String fallbackRootPath
    ) {
        var effectiveTarget = target == null ? new ArchDoxAgentProperties.StorageTarget() : target;
        var rootPath = hasText(effectiveTarget.getRootPath()) ? effectiveTarget.getRootPath() : fallbackRootPath;
        return new AgentStorageTargetProfile(
                AgentStorageKind.from(effectiveTarget.getKind()),
                rootPath,
                effectiveTarget.getBucket(),
                effectiveTarget.getPrefix());
    }

    public Map<String, Object> publicProfile() {
        var value = new LinkedHashMap<String, Object>();
        value.put("kind", kind.name());
        value.put("fileSystemBacked", kind.isFileSystemBacked());
        value.put("rootConfigured", kind.isFileSystemBacked() && hasText(rootPath));
        if (kind == AgentStorageKind.S3_COMPATIBLE) {
            value.put("bucketConfigured", hasText(bucket));
        }
        if (hasText(bucket)) {
            value.put("bucket", bucket);
        }
        if (hasText(prefix)) {
            value.put("prefix", prefix);
        }
        return value;
    }

    public void requireFileSystemBacked(String usage) {
        if (!kind.isFileSystemBacked()) {
            throw new UnsupportedOperationException(
                    "ArchDox Agent " + usage + " storage kind " + kind
                            + " is configured, but this runtime currently supports LOCAL_FILE/NAS filesystem storage only");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

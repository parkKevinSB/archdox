package com.archdox.agent.storage;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AgentFileSystemStorageSupport {
    private final String usage;
    private final Path root;

    public AgentFileSystemStorageSupport(String usage, AgentStorageTargetProfile profile) {
        this.usage = usage;
        profile.requireFileSystemBacked(usage);
        if (profile.rootPath() == null || profile.rootPath().isBlank()) {
            throw new IllegalArgumentException("ArchDox Agent " + usage + " root path is required");
        }
        this.root = Paths.get(profile.rootPath()).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public String normalize(String logicalRef) {
        return AgentStorageRefNormalizer.normalize(usage, logicalRef);
    }

    public Path resolve(String logicalRef) {
        var target = root.resolve(logicalRef).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid ArchDox Agent " + usage + " storage reference");
        }
        return target;
    }
}

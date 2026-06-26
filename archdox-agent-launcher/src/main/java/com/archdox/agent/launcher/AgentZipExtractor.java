package com.archdox.agent.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

public class AgentZipExtractor {
    public void extract(Path zipFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (var input = new ZipInputStream(Files.newInputStream(zipFile))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                var target = targetDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(targetDir.normalize())) {
                    throw new IllegalStateException("Runtime package contains an unsafe zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(input, target);
            }
        }
    }
}

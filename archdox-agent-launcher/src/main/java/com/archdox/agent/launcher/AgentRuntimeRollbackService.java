package com.archdox.agent.launcher;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

public class AgentRuntimeRollbackService {
    public boolean rollback(Path installDir) throws IOException {
        var current = installDir.resolve("current");
        var previous = installDir.resolve("previous");
        if (!Files.exists(previous)) {
            return false;
        }
        var failed = installDir.resolve("failed-" + System.currentTimeMillis());
        if (Files.exists(current)) {
            moveDirectory(current, failed);
        }
        try {
            moveDirectory(previous, current);
        } catch (IOException ex) {
            if (Files.exists(failed) && !Files.exists(current)) {
                moveDirectory(failed, current);
            }
            throw ex;
        }
        deleteRecursively(failed);
        return true;
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            var paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path candidate : paths) {
                Files.deleteIfExists(candidate);
            }
        }
    }
}

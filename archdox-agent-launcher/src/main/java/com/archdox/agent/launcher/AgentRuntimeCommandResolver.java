package com.archdox.agent.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AgentRuntimeCommandResolver {
    public List<String> resolve(Path runtimeDir, String explicitCommand) throws IOException {
        if (explicitCommand != null && !explicitCommand.isBlank()) {
            return splitCommand(explicitCommand);
        }
        var executable = script(runtimeDir);
        if (executable != null) {
            return List.of(executable.toAbsolutePath().toString());
        }
        var jar = jar(runtimeDir);
        if (jar != null) {
            return List.of("java", "-jar", jar.toAbsolutePath().toString());
        }
        throw new IllegalStateException("Cannot find Agent runtime executable under " + runtimeDir);
    }

    private Path script(Path runtimeDir) {
        var windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        var preferred = runtimeDir.resolve("bin").resolve(windows ? "archdox-agent.bat" : "archdox-agent");
        if (Files.isRegularFile(preferred)) {
            return preferred;
        }
        var fallback = runtimeDir.resolve("bin").resolve(windows ? "archdox-agent" : "archdox-agent.bat");
        if (Files.isRegularFile(fallback)) {
            return fallback;
        }
        return null;
    }

    private Path jar(Path runtimeDir) throws IOException {
        try (var stream = Files.walk(runtimeDir, 3)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> path.getFileName().toString().contains("archdox-agent"))
                    .min(Comparator.comparingInt(path -> path.getNameCount()))
                    .orElse(null);
        }
    }

    private List<String> splitCommand(String command) {
        var parts = new ArrayList<String>();
        var current = new StringBuilder();
        var quoted = false;
        for (int i = 0; i < command.length(); i++) {
            var ch = command.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
                continue;
            }
            if (Character.isWhitespace(ch) && !quoted) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Runtime command is blank.");
        }
        return parts;
    }
}

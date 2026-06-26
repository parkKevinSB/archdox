package com.archdox.agent.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;

public class AgentRuntimeStateStore {
    private static final String STATE_FILE = "launcher-state.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<AgentRuntimeState> read(Path workDir) throws IOException {
        var path = statePath(workDir);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(path.toFile(), AgentRuntimeState.class));
    }

    public void write(Path workDir, AgentRuntimeState state) throws IOException {
        Files.createDirectories(workDir);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(statePath(workDir).toFile(), state);
    }

    public AgentRuntimeState state(
            String status,
            Long pid,
            String command,
            String healthUrl,
            String stdoutLog,
            String stderrLog,
            String reason
    ) {
        return new AgentRuntimeState(
                status,
                pid,
                command,
                healthUrl,
                stdoutLog,
                stderrLog,
                reason,
                OffsetDateTime.now().toString());
    }

    private Path statePath(Path workDir) {
        return workDir.resolve(STATE_FILE);
    }
}

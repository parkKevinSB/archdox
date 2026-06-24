package com.archdox.cloud.platformops.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.platformops.domain.PlatformOpsLogProjectionCursor;
import com.archdox.cloud.platformops.infra.PlatformOpsLogProjectionCursorRepository;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.PageRequest;

class PlatformOpsLogProjectionServiceTest {
    @TempDir
    java.nio.file.Path tempDir;

    private final PlatformOpsLogProjectionProperties properties = new PlatformOpsLogProjectionProperties();
    private final PlatformOpsLogProjectionCursorRepository repository = mock(PlatformOpsLogProjectionCursorRepository.class);

    @Test
    void projectsWarnAndErrorLogsWithSanitizedEvidenceAndCursor() throws Exception {
        var logFile = tempDir.resolve("archdox-cloud-api.log");
        Files.writeString(logFile, """
                2026-06-25T01:00:00.000+09:00 INFO  [main] [no-corr] [ ] com.archdox.Startup - started
                2026-06-25T01:01:00.000+09:00 WARN  [http-nio-8080-exec-1] [corr-1] [GET /api/v1/test] com.archdox.TestService - Slow request token=should-not-leak
                2026-06-25T01:02:00.000+09:00 ERROR [flower-worker-1] [corr-2] [POST /api/v1/documents] com.archdox.DocumentService - Failed with Bearer abc.def.ghi password=hidden
                java.lang.IllegalStateException: stack trace should not be stored
                """);
        properties.setLogFilePath(logFile.toString());
        properties.setTailOnFirstScan(false);
        when(repository.findById("cloud-api")).thenReturn(Optional.empty());
        when(repository.save(any(PlatformOpsLogProjectionCursor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var findings = service().project(
                OffsetDateTime.parse("2026-06-25T01:03:00+09:00"),
                PageRequest.of(0, 10));

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(PlatformOpsDetectionFinding::code)
                .containsExactly("APPLICATION_LOG_WARN_DETECTED", "APPLICATION_LOG_ERROR_DETECTED");
        assertThat(findings.get(0).message()).doesNotContain("should-not-leak");
        assertThat(findings.get(1).message()).doesNotContain("hidden");
        assertThat(findings.get(1).evidenceJson())
                .containsEntry("rawLogStored", false)
                .containsEntry("stackTraceStored", false);
    }

    @Test
    void resumesFromStoredCursorWithoutReprojectingOldLines() throws Exception {
        var logFile = tempDir.resolve("archdox-cloud-api.log");
        var oldLine = "2026-06-25T01:01:00.000+09:00 ERROR [main] [corr-1] [ ] com.archdox.Old - Old failure\n";
        var newLine = "2026-06-25T01:02:00.000+09:00 WARN  [main] [corr-2] [ ] com.archdox.New - New warning\n";
        Files.writeString(logFile, oldLine + newLine);
        properties.setLogFilePath(logFile.toString());
        properties.setTailOnFirstScan(false);
        var cursor = new PlatformOpsLogProjectionCursor(
                "cloud-api",
                logFile.toString(),
                OffsetDateTime.parse("2026-06-25T01:00:00+09:00"));
        cursor.advance(
                logFile.toString(),
                oldLine.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                oldLine.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                OffsetDateTime.parse("2026-06-25T01:01:00+09:00"));
        when(repository.findById("cloud-api")).thenReturn(Optional.of(cursor));
        when(repository.save(any(PlatformOpsLogProjectionCursor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var findings = service().project(
                OffsetDateTime.parse("2026-06-25T01:03:00+09:00"),
                PageRequest.of(0, 10));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).isEqualTo("New warning");
        assertThat(cursor.positionBytes()).isEqualTo(Files.size(logFile));
    }

    private PlatformOpsLogProjectionService service() {
        return new PlatformOpsLogProjectionService(properties, repository);
    }
}

package com.archdox.cloud.platformops.application;

import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsLogProjectionCursor;
import com.archdox.cloud.platformops.infra.PlatformOpsLogProjectionCursorRepository;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class PlatformOpsLogProjectionService {
    public static final String WORKFLOW_TYPE = "platform-log-projection";

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2})\\s+"
                    + "(?<level>WARN|ERROR)\\s+"
                    + "\\[(?<thread>[^]]*)]\\s+"
                    + "\\[(?<correlationId>[^]]*)]\\s+"
                    + "\\[(?<request>[^]]*)]\\s+"
                    + "(?<logger>\\S+)\\s+-\\s+"
                    + "(?<message>.*)$");
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}\\b");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern SECRET_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)\\b(api[-_ ]?key|authorization|jwt|password|secret|token)(\\s*[:=]\\s*)([^\\s,;]+)");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b");

    private final PlatformOpsLogProjectionProperties properties;
    private final PlatformOpsLogProjectionCursorRepository cursorRepository;

    public PlatformOpsLogProjectionService(
            PlatformOpsLogProjectionProperties properties,
            PlatformOpsLogProjectionCursorRepository cursorRepository
    ) {
        this.properties = properties;
        this.cursorRepository = cursorRepository;
    }

    public List<PlatformOpsDetectionFinding> project(OffsetDateTime now, Pageable page) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        var path = logPath();
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try {
            return projectReadableFile(path, now, Math.min(page.getPageSize(), properties.safeMaxEventsPerScan()));
        } catch (IOException ex) {
            return List.of(logReadFailure(path, ex, now));
        }
    }

    private List<PlatformOpsDetectionFinding> projectReadableFile(Path path, OffsetDateTime now, int maxEvents) throws IOException {
        var sourceCode = properties.safeSourceCode();
        var cursor = cursorRepository.findById(sourceCode)
                .orElseGet(() -> new PlatformOpsLogProjectionCursor(sourceCode, path.toString(), now));
        var fileSize = Files.size(path);
        var position = nextPosition(cursor, fileSize);
        var maxBytes = Math.min(properties.safeMaxBytesPerScan(), Math.max(0, fileSize - position));
        if (maxBytes <= 0) {
            cursor.advance(path.toString(), fileSize, fileSize, now);
            cursorRepository.save(cursor);
            return List.of();
        }

        var read = readBytes(path, position, maxBytes);
        var findings = new ArrayList<PlatformOpsDetectionFinding>();
        var consumed = 0L;
        var lineStart = 0;
        for (var index = 0; index < read.length && findings.size() < maxEvents; index++) {
            if (read[index] == '\n') {
                var lineEnd = index > lineStart && read[index - 1] == '\r' ? index - 1 : index;
                var line = new String(Arrays.copyOfRange(read, lineStart, lineEnd), StandardCharsets.UTF_8);
                consumed = index + 1L;
                parseLine(line, path, position + lineStart, now).ifPresent(findings::add);
                lineStart = index + 1;
            }
        }
        if (findings.size() < maxEvents && lineStart < read.length) {
            // Leave an incomplete tail line for the next scan.
            consumed = lineStart;
        }
        cursor.advance(path.toString(), position + consumed, fileSize, now);
        cursorRepository.save(cursor);
        return findings;
    }

    private long nextPosition(PlatformOpsLogProjectionCursor cursor, long fileSize) {
        if (cursor.lastScannedAt() == null && properties.isTailOnFirstScan()) {
            return Math.max(0, fileSize - properties.safeMaxBytesPerScan());
        }
        if (fileSize < cursor.positionBytes()) {
            return Math.max(0, fileSize - properties.safeMaxBytesPerScan());
        }
        return Math.max(0, cursor.positionBytes());
    }

    private byte[] readBytes(Path path, long position, long maxBytes) throws IOException {
        var buffer = ByteBuffer.allocate((int) maxBytes);
        try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.position(position);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                // read bounded chunk
            }
        }
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    private Optional<PlatformOpsDetectionFinding> parseLine(
            String line,
            Path path,
            long byteOffset,
            OffsetDateTime now
    ) {
        var matcher = LOG_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        var level = matcher.group("level");
        var logger = matcher.group("logger");
        var message = trimTo(sanitize(matcher.group("message")), properties.safeMaxMessageLength());
        if (message.isBlank()) {
            return Optional.empty();
        }
        var occurredAt = matcher.group("timestamp");
        var correlationId = blankToNull(matcher.group("correlationId"));
        var request = blankToNull(matcher.group("request"));
        var fingerprint = fingerprint(level, logger, message);
        var severity = "ERROR".equals(level) ? PlatformOpsFindingSeverity.ERROR : PlatformOpsFindingSeverity.WARN;
        var category = "ERROR".equals(level) ? "APPLICATION_LOG_ERROR" : "APPLICATION_LOG_WARN";
        var code = "ERROR".equals(level) ? "APPLICATION_LOG_ERROR_DETECTED" : "APPLICATION_LOG_WARN_DETECTED";
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("sourceCode", properties.safeSourceCode());
        evidence.put("logPath", path.toString());
        evidence.put("level", level);
        evidence.put("logger", logger);
        evidence.put("occurredAt", occurredAt);
        evidence.put("byteOffset", byteOffset);
        evidence.put("fingerprint", fingerprint);
        evidence.put("messageSample", message);
        evidence.put("rawLogStored", false);
        evidence.put("stackTraceStored", false);
        if (correlationId != null && !"no-corr".equals(correlationId)) {
            evidence.put("correlationId", sanitize(correlationId));
        }
        if (request != null) {
            evidence.put("request", sanitize(request));
        }
        return Optional.of(new PlatformOpsDetectionFinding(
                null,
                severity,
                category,
                code,
                "Application " + level + " log detected",
                message,
                "APPLICATION_LOG",
                fingerprint,
                WORKFLOW_TYPE,
                properties.safeSourceCode() + ":" + fingerprint,
                Map.copyOf(evidence),
                "Review the structured log projection, related operation events, and matching correlation id before escalating to AI diagnosis."));
    }

    private PlatformOpsDetectionFinding logReadFailure(Path path, IOException ex, OffsetDateTime now) {
        var message = trimTo(sanitize(ex.getClass().getSimpleName() + ": " + ex.getMessage()), properties.safeMaxMessageLength());
        var fingerprint = fingerprint("WARN", "PlatformOpsLogProjectionService", message);
        return new PlatformOpsDetectionFinding(
                null,
                PlatformOpsFindingSeverity.WARN,
                "APPLICATION_LOG_PROJECTION",
                "APPLICATION_LOG_PROJECTION_FAILED",
                "Application log projection failed",
                message,
                "APPLICATION_LOG",
                fingerprint,
                WORKFLOW_TYPE,
                properties.safeSourceCode() + ":" + fingerprint,
                Map.of(
                        "sourceCode", properties.safeSourceCode(),
                        "logPath", path.toString(),
                        "failure", message,
                        "rawLogStored", false,
                        "occurredAt", now.toString()),
                "Check log path permissions and the configured platform ops log projection path.");
    }

    private Path logPath() {
        return Path.of(properties.getLogFilePath()).toAbsolutePath().normalize();
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        var sanitized = JWT_PATTERN.matcher(value).replaceAll("[REDACTED_JWT]");
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
        sanitized = SECRET_ASSIGNMENT_PATTERN.matcher(sanitized).replaceAll("$1$2[REDACTED]");
        return sanitized.replace('\u0000', ' ').trim();
    }

    private String fingerprint(String level, String logger, String message) {
        var normalizedMessage = NUMBER_PATTERN.matcher(UUID_PATTERN.matcher(message)
                        .replaceAll("#uuid"))
                .replaceAll("#")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
        var source = properties.safeSourceCode()
                + "|"
                + level
                + "|"
                + logger
                + "|"
                + normalizedMessage;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create log projection fingerprint", ex);
        }
    }

    private String trimTo(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

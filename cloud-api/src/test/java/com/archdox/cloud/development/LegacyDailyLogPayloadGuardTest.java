package com.archdox.cloud.development;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyDailyLogPayloadGuardTest {
    private static final List<ForbiddenPattern> FORBIDDEN_PATTERNS = List.of(
            new ForbiddenPattern(
                    "cloud-api/src/main/java",
                    "get(\"supervisionContent\")",
                    "Do not read legacy DAILY_LOG entry.supervisionContent as canonical input."),
            new ForbiddenPattern(
                    "cloud-api/src/main/java",
                    "entry.put(\"supervisionContent\"",
                    "Do not write generated prose back into DAILY_LOG entry payload."),
            new ForbiddenPattern(
                    "cloud-api/src/main/java",
                    "text(item, \"supervisionContent\")",
                    "Ledger/document projection must derive content from checklistRows."),
            new ForbiddenPattern(
                    "cloud-api/src/main/java",
                    "DAILY_LOG.entries",
                    "Do not accept old flat DAILY_LOG.entries auto-fix paths in production code."),
            new ForbiddenPattern(
                    "document-engine/src/main/java",
                    "get(\"supervisionContent\")",
                    "Document engine must use render context, not legacy report payload fields."),
            new ForbiddenPattern(
                    "archdox-ai-harness/src/main/java",
                    "invalid legacy payload",
                    "Prompts should describe invalid report payloads, not preserve legacy compatibility."),
            new ForbiddenPattern(
                    "client/web/src/features/reports",
                    "supervisionContent",
                    "Report writing UI must store checklistRows, not a legacy supervisionContent input field."),
            new ForbiddenPattern(
                    "client/web/src/features/documents",
                    "DAILY_LOG.entries",
                    "Document UI must not target old flat DAILY_LOG.entries paths.")
    );

    @Test
    void productionCodeDoesNotReintroduceLegacyDailyLogPayloadFallback() throws IOException {
        var repoRoot = findRepoRoot();
        var violations = new ArrayList<String>();

        for (var pattern : FORBIDDEN_PATTERNS) {
            var root = repoRoot.resolve(pattern.relativeRoot());
            if (!Files.exists(root)) {
                continue;
            }
            try (var files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(LegacyDailyLogPayloadGuardTest::isSourceFile)
                        .forEach(file -> collectViolations(file, pattern, violations));
            }
        }

        if (!violations.isEmpty()) {
            fail("Legacy DAILY_LOG payload fallback patterns are forbidden:\n" + String.join("\n", violations));
        }
    }

    private static void collectViolations(Path file, ForbiddenPattern pattern, List<String> violations) {
        try {
            var content = Files.readString(file, StandardCharsets.UTF_8);
            if (!content.contains(pattern.needle())) {
                return;
            }
            violations.add(file + " contains `" + pattern.needle() + "`: " + pattern.reason());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan " + file, exception);
        }
    }

    private static boolean isSourceFile(Path file) {
        var name = file.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".ts") || name.endsWith(".tsx");
    }

    private static Path findRepoRoot() {
        var current = Path.of("").toAbsolutePath();
        for (var candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not locate ArchDox repository root from " + current);
    }

    private record ForbiddenPattern(String relativeRoot, String needle, String reason) {
    }
}

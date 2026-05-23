package com.archdox.agent.document;

import com.archdox.document.LibreOfficeCommandRunner;
import com.archdox.document.ProcessLibreOfficeCommandRunner;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LibreOfficeRuntimeAvailability {
    private static final long PROBE_TIMEOUT_MS = 5000;

    private final DocumentExportProperties exportProperties;
    private final LibreOfficeCommandRunner commandRunner;
    private volatile LibreOfficeRuntimeStatus cachedStatus;

    @Autowired
    public LibreOfficeRuntimeAvailability(DocumentExportProperties exportProperties) {
        this(exportProperties, new ProcessLibreOfficeCommandRunner());
    }

    public LibreOfficeRuntimeAvailability(
            DocumentExportProperties exportProperties,
            LibreOfficeCommandRunner commandRunner
    ) {
        this.exportProperties = exportProperties;
        this.commandRunner = commandRunner == null ? new ProcessLibreOfficeCommandRunner() : commandRunner;
    }

    public LibreOfficeRuntimeStatus probe() {
        var libreOffice = exportProperties.getLibreOffice();
        var executablePath = executablePath(libreOffice);
        if (!libreOffice.isEnabled()) {
            return LibreOfficeRuntimeStatus.disabled(executablePath);
        }
        var cached = cachedStatus;
        if (cached != null && cached.matches(true, executablePath)) {
            return cached;
        }
        synchronized (this) {
            cached = cachedStatus;
            if (cached != null && cached.matches(true, executablePath)) {
                return cached;
            }
            cachedStatus = runProbe(executablePath);
            return cachedStatus;
        }
    }

    private LibreOfficeRuntimeStatus runProbe(String executablePath) {
        try {
            var result = commandRunner.run(
                    List.of(executablePath, "--version"),
                    Duration.ofMillis(PROBE_TIMEOUT_MS));
            if (result.timedOut()) {
                return LibreOfficeRuntimeStatus.unavailable(
                        executablePath,
                        "LibreOffice probe timed out after " + PROBE_TIMEOUT_MS + " ms.");
            }
            if (result.exitCode() != 0) {
                return LibreOfficeRuntimeStatus.unavailable(
                        executablePath,
                        "LibreOffice probe failed with exit code " + result.exitCode() + ".");
            }
            return LibreOfficeRuntimeStatus.available(executablePath, firstLine(result.output()));
        } catch (IOException ex) {
            return LibreOfficeRuntimeStatus.unavailable(
                    executablePath,
                    "LibreOffice executable is not available: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return LibreOfficeRuntimeStatus.unavailable(
                    executablePath,
                    "LibreOffice probe was interrupted.");
        }
    }

    private String executablePath(DocumentExportProperties.LibreOffice libreOffice) {
        var value = libreOffice.getExecutablePath();
        return value == null || value.isBlank() ? "soffice" : value;
    }

    private String firstLine(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        return output.strip().lines().findFirst().orElse("");
    }
}

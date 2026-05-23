package com.archdox.agent.document;

public record LibreOfficeRuntimeStatus(
        boolean enabled,
        boolean available,
        String executablePath,
        String version,
        String message
) {
    public static LibreOfficeRuntimeStatus disabled(String executablePath) {
        return new LibreOfficeRuntimeStatus(false, false, executablePath, "", "LibreOffice export is disabled.");
    }

    public static LibreOfficeRuntimeStatus available(String executablePath, String version) {
        return new LibreOfficeRuntimeStatus(true, true, executablePath, version == null ? "" : version, "available");
    }

    public static LibreOfficeRuntimeStatus unavailable(String executablePath, String message) {
        return new LibreOfficeRuntimeStatus(true, false, executablePath, "", message == null ? "unavailable" : message);
    }

    public boolean pdfExportAvailable() {
        return enabled && available;
    }

    boolean matches(boolean enabled, String executablePath) {
        return this.enabled == enabled && this.executablePath.equals(executablePath);
    }
}

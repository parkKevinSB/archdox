package com.archdox.document;

public record LibreOfficePdfExportOptions(
        String executablePath,
        long timeoutMs
) {
    public LibreOfficePdfExportOptions {
        executablePath = executablePath == null || executablePath.isBlank() ? "soffice" : executablePath;
        timeoutMs = Math.max(1, timeoutMs);
    }

    public static LibreOfficePdfExportOptions defaults() {
        return new LibreOfficePdfExportOptions("soffice", 60000);
    }
}

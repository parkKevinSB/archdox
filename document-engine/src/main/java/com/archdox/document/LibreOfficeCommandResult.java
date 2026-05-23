package com.archdox.document;

public record LibreOfficeCommandResult(
        int exitCode,
        String output,
        boolean timedOut
) {
}

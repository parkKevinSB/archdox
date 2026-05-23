package com.archdox.document;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@FunctionalInterface
public interface LibreOfficeCommandRunner {
    LibreOfficeCommandResult run(List<String> command, Duration timeout) throws IOException, InterruptedException;
}

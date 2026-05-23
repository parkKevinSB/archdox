package com.archdox.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ProcessLibreOfficeCommandRunner implements LibreOfficeCommandRunner {
    @Override
    public LibreOfficeCommandResult run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        var process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        var outputFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                return ex.getMessage();
            }
        });
        var completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            return new LibreOfficeCommandResult(-1, output(outputFuture), true);
        }
        return new LibreOfficeCommandResult(process.exitValue(), output(outputFuture), false);
    }

    private String output(CompletableFuture<String> outputFuture) {
        try {
            return outputFuture.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "";
        } catch (TimeoutException | java.util.concurrent.ExecutionException ex) {
            return "";
        }
    }
}

package com.archdox.worker.application;

public interface ArchDoxWorkerTraceSink {
    void record(ArchDoxWorkerTraceEvent event);

    static ArchDoxWorkerTraceSink noop() {
        return event -> {
        };
    }
}

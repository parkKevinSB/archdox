package com.archdox.cloud.monitoring.application;

import java.time.OffsetDateTime;

public interface ServerRuntimeHealthProbe {
    ServerRuntimeHealthMetrics sample(OffsetDateTime capturedAt);
}

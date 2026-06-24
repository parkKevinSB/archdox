package com.archdox.cloud.platformops.domain;

public enum PlatformOpsRunTriggerType {
    MANUAL_DETECT_STUCK,
    AUTO_DETECT_STUCK,
    MANUAL_DIAGNOSIS,
    AUTO_DIAGNOSIS,
    AUTO_SYSTEM_DIAGNOSIS,
    DETECTOR_TRIGGERED,
    AUTO_DAILY_REPORT,
    MANUAL_DAILY_REPORT
}

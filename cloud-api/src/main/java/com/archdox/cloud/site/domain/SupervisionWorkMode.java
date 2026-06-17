package com.archdox.cloud.site.domain;

import java.util.Locale;

public enum SupervisionWorkMode {
    NON_RESIDENT,
    RESIDENT,
    RESPONSIBLE_RESIDENT;

    public static SupervisionWorkMode defaultMode() {
        return NON_RESIDENT;
    }

    public static SupervisionWorkMode normalize(String value) {
        if (value == null || value.isBlank()) {
            return defaultMode();
        }
        return SupervisionWorkMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

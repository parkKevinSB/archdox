package com.archdox.cloud.office.application;

import com.archdox.cloud.global.api.BadRequestException;

public final class OfficeContext {
    private static final ThreadLocal<Long> CURRENT_OFFICE_ID = new ThreadLocal<>();

    private OfficeContext() {
    }

    public static void set(Long officeId) {
        CURRENT_OFFICE_ID.set(officeId);
    }

    public static Long currentOfficeIdOrNull() {
        return CURRENT_OFFICE_ID.get();
    }

    public static Long requireCurrentOfficeId() {
        var officeId = CURRENT_OFFICE_ID.get();
        if (officeId == null) {
            throw new BadRequestException("X-Office-Id header is required");
        }
        return officeId;
    }

    public static void clear() {
        CURRENT_OFFICE_ID.remove();
    }
}

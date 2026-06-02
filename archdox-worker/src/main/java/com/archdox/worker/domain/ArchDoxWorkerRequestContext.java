package com.archdox.worker.domain;

public record ArchDoxWorkerRequestContext(
        Long userId,
        Long officeId,
        Long projectId,
        Long siteId,
        Long reportId,
        Long documentJobId,
        String locale
) {
    public ArchDoxWorkerRequestContext {
        locale = locale == null || locale.isBlank() ? "ko-KR" : locale.trim();
    }

    public static ArchDoxWorkerRequestContext empty() {
        return new ArchDoxWorkerRequestContext(null, null, null, null, null, null, "ko-KR");
    }
}

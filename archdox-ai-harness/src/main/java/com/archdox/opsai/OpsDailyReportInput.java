package com.archdox.opsai;

import java.util.Map;

public record OpsDailyReportInput(
        String opsRunId,
        String dueAt,
        String periodFrom,
        String periodTo,
        Map<String, Object> redactedSnapshot
) {
    public OpsDailyReportInput {
        opsRunId = opsRunId == null ? "" : opsRunId.trim();
        dueAt = dueAt == null ? "" : dueAt.trim();
        periodFrom = periodFrom == null ? "" : periodFrom.trim();
        periodTo = periodTo == null ? "" : periodTo.trim();
        redactedSnapshot = redactedSnapshot == null ? Map.of() : Map.copyOf(redactedSnapshot);
    }
}

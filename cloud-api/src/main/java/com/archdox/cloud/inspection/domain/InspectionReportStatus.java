package com.archdox.cloud.inspection.domain;

public enum InspectionReportStatus {
    DRAFT,
    STEP_SAVED,
    READY_TO_GENERATE,
    GENERATION_REQUESTED,
    GENERATING,
    GENERATED,
    DELIVERED,
    FAILED,
    CANCELLED
}

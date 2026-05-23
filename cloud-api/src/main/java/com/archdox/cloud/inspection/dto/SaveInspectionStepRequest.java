package com.archdox.cloud.inspection.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record SaveInspectionStepRequest(
        @NotNull Map<String, Object> payload
) {
}

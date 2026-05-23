package com.archdox.cloud.inspectiontarget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateInspectionTargetRequest(
        Long parentTargetId,
        @NotBlank @Size(max = 80) String targetType,
        @Size(max = 80) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String address,
        Map<String, Object> metadata
) {
}

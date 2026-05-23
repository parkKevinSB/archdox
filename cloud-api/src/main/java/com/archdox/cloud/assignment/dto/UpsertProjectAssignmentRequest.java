package com.archdox.cloud.assignment.dto;

import com.archdox.cloud.assignment.domain.ProjectAssignmentRole;
import jakarta.validation.constraints.NotNull;

public record UpsertProjectAssignmentRequest(
        @NotNull Long userId,
        @NotNull ProjectAssignmentRole role
) {
}

package com.archdox.cloud.assignment.dto;

import com.archdox.cloud.assignment.domain.AssignmentStatus;
import com.archdox.cloud.assignment.domain.ProjectAssignmentRole;
import java.time.OffsetDateTime;

public record ProjectAssignmentResponse(
        Long id,
        Long officeId,
        Long projectId,
        Long userId,
        String email,
        String name,
        ProjectAssignmentRole role,
        AssignmentStatus status,
        Long assignedBy,
        OffsetDateTime assignedAt,
        OffsetDateTime updatedAt
) {
}

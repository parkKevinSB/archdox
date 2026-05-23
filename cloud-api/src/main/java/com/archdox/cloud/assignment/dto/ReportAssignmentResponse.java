package com.archdox.cloud.assignment.dto;

import com.archdox.cloud.assignment.domain.AssignmentStatus;
import com.archdox.cloud.assignment.domain.ReportAssignmentRole;
import java.time.OffsetDateTime;

public record ReportAssignmentResponse(
        Long id,
        Long officeId,
        Long reportId,
        Long userId,
        String email,
        String name,
        ReportAssignmentRole role,
        AssignmentStatus status,
        Long assignedBy,
        OffsetDateTime assignedAt,
        OffsetDateTime updatedAt
) {
}

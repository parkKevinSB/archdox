package com.archdox.cloud.project.dto;

import com.archdox.cloud.project.domain.ProjectStatus;
import java.time.LocalDate;

public record ProjectResponse(
        Long id,
        Long officeId,
        String name,
        String address,
        String buildingType,
        LocalDate startDate,
        LocalDate endDate,
        ProjectStatus status,
        boolean manageAllowed,
        boolean structureManageAllowed,
        boolean reportCreateAllowed
) {
}

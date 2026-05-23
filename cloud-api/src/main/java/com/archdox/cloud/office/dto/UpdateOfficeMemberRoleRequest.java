package com.archdox.cloud.office.dto;

import com.archdox.shared.MembershipRole;
import jakarta.validation.constraints.NotNull;

public record UpdateOfficeMemberRoleRequest(
        @NotNull MembershipRole role
) {
}

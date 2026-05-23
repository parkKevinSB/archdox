package com.archdox.cloud.office.dto;

import com.archdox.shared.MembershipRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddOfficeMemberRequest(
        @Email @NotBlank String email,
        @NotNull MembershipRole role
) {
}

package com.archdox.cloud.office.dto;

import com.archdox.shared.MembershipRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOfficeInvitationRequest(
        @Email @NotBlank String email,
        @NotNull MembershipRole role,
        @Min(1) @Max(30) Integer expiresInDays
) {
}

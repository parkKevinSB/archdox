package com.archdox.cloud.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email @NotBlank String email,
        @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String name,
        SignupAccountType accountType,
        @Size(max = 100) String officeCode,
        @Size(max = 500) String invitationToken
) {
    public SignupAccountType resolvedAccountType() {
        return accountType == null ? SignupAccountType.PERSONAL : accountType;
    }
}

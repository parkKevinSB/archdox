package com.archdox.cloud.office.dto;

import com.archdox.cloud.office.domain.OfficeInvitationStatus;
import com.archdox.shared.MembershipRole;
import java.time.OffsetDateTime;

public record OfficeInvitationPreviewResponse(
        String email,
        Long officeId,
        String officeCode,
        String officeDisplayName,
        MembershipRole role,
        OfficeInvitationStatus status,
        OffsetDateTime expiresAt
) {
}

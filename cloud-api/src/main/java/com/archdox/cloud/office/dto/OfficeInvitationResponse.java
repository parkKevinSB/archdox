package com.archdox.cloud.office.dto;

import com.archdox.cloud.office.domain.OfficeInvitationStatus;
import com.archdox.shared.MembershipRole;
import java.time.OffsetDateTime;

public record OfficeInvitationResponse(
        Long id,
        Long officeId,
        String email,
        MembershipRole role,
        OfficeInvitationStatus status,
        Long invitedByUserId,
        Long acceptedByUserId,
        String tokenPreview,
        String acceptToken,
        String acceptPath,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        OffsetDateTime acceptedAt,
        OffsetDateTime cancelledAt,
        OffsetDateTime updatedAt
) {
}

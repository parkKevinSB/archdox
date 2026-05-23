package com.archdox.cloud.office.dto;

import com.archdox.shared.MembershipRole;
import com.archdox.shared.MembershipStatus;
import java.time.OffsetDateTime;

public record OfficeMemberResponse(
        Long membershipId,
        Long userId,
        Long officeId,
        String email,
        String name,
        MembershipRole role,
        MembershipStatus status,
        OffsetDateTime joinedAt
) {
}

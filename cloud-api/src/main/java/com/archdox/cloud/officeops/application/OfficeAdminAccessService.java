package com.archdox.cloud.officeops.application;

import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.shared.MembershipRole;
import com.archdox.shared.MembershipStatus;
import org.springframework.stereotype.Service;

@Service
public class OfficeAdminAccessService {
    private final OfficeMembershipRepository membershipRepository;
    private final PlatformAdminService platformAdminService;

    public OfficeAdminAccessService(
            OfficeMembershipRepository membershipRepository,
            PlatformAdminService platformAdminService
    ) {
        this.membershipRepository = membershipRepository;
        this.platformAdminService = platformAdminService;
    }

    public Long requireOfficeAdmin(UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        if (platformAdminService.isPlatformAdmin(principal)) {
            return officeId;
        }
        var membership = membershipRepository.findByUserIdAndOfficeIdAndStatus(
                        principal.userId(),
                        officeId,
                        MembershipStatus.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("Office membership required"));
        if (membership.role() != MembershipRole.OWNER && membership.role() != MembershipRole.ADMIN) {
            throw new ForbiddenException("Office admin role required");
        }
        return officeId;
    }
}

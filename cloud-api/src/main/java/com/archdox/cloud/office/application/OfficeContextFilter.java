package com.archdox.cloud.office.application;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.shared.MembershipStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OfficeContextFilter extends OncePerRequestFilter {
    private final OfficeMembershipRepository membershipRepository;
    private final PlatformAdminService platformAdminService;

    public OfficeContextFilter(
            OfficeMembershipRepository membershipRepository,
            PlatformAdminService platformAdminService
    ) {
        this.membershipRepository = membershipRepository;
        this.platformAdminService = platformAdminService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            var officeHeader = request.getHeader("X-Office-Id");
            if (officeHeader != null && !officeHeader.isBlank()) {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required for X-Office-Id");
                    return;
                }
                var officeId = parseOfficeId(officeHeader);
                if (officeId == null) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid X-Office-Id");
                    return;
                }
                if (!platformAdminService.isPlatformAdmin(principal) && !membershipRepository.existsByUserIdAndOfficeIdAndStatus(
                        principal.userId(),
                        officeId,
                        MembershipStatus.ACTIVE)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Office membership required");
                    return;
                }
                OfficeContext.set(officeId);
            }
            chain.doFilter(request, response);
        } finally {
            OfficeContext.clear();
        }
    }

    private Long parseOfficeId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

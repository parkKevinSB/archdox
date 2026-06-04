package com.archdox.cloud.office.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.shared.MembershipStatus;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class OfficeContextFilterTest {
    @Mock
    OfficeMembershipRepository membershipRepository;

    @Mock
    PlatformAdminService platformAdminService;

    @Mock
    FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        OfficeContext.clear();
    }

    @Test
    void rejectsNonActiveMembership() throws Exception {
        var filter = new OfficeContextFilter(membershipRepository, platformAdminService);
        var request = new MockHttpServletRequest("GET", "/api/v1/projects");
        var response = new MockHttpServletResponse();
        request.addHeader("X-Office-Id", "10");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(1L, "user@example.com"),
                null));

        when(membershipRepository.existsByUserIdAndOfficeIdAndStatus(1L, 10L, MembershipStatus.ACTIVE))
                .thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void setsOfficeContextForActiveMembership() throws Exception {
        var filter = new OfficeContextFilter(membershipRepository, platformAdminService);
        var request = new MockHttpServletRequest("GET", "/api/v1/projects");
        var response = new MockHttpServletResponse();
        request.addHeader("X-Office-Id", "10");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(1L, "user@example.com"),
                null));

        when(membershipRepository.existsByUserIdAndOfficeIdAndStatus(1L, 10L, MembershipStatus.ACTIVE))
                .thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void platformAdminCanSetOfficeContextWithoutMembership() throws Exception {
        var filter = new OfficeContextFilter(membershipRepository, platformAdminService);
        var request = new MockHttpServletRequest("GET", "/api/v1/projects");
        var response = new MockHttpServletResponse();
        var principal = new UserPrincipal(1L, "admin@example.com");
        request.addHeader("X-Office-Id", "10");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,
                null));

        when(platformAdminService.isPlatformAdmin(principal)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
        verify(membershipRepository, never()).existsByUserIdAndOfficeIdAndStatus(
                1L,
                10L,
                MembershipStatus.ACTIVE);
    }
}

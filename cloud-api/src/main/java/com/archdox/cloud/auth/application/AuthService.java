package com.archdox.cloud.auth.application;

import com.archdox.cloud.account.domain.AuthRefreshToken;
import com.archdox.cloud.account.domain.UserAccount;
import com.archdox.cloud.account.infra.AuthRefreshTokenRepository;
import com.archdox.cloud.account.infra.UserAccountRepository;
import com.archdox.cloud.auth.dto.AuthTokenResponse;
import com.archdox.cloud.auth.dto.MeResponse;
import com.archdox.cloud.auth.dto.OfficePermissionSummaryResponse;
import com.archdox.cloud.auth.dto.OfficeSummaryResponse;
import com.archdox.cloud.auth.dto.SignupAccountType;
import com.archdox.cloud.auth.dto.SignupRequest;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.ConflictException;
import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.api.UnauthorizedException;
import com.archdox.cloud.global.security.JwtProperties;
import com.archdox.cloud.global.security.JwtService;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.domain.Office;
import com.archdox.cloud.office.domain.OfficeInvitationStatus;
import com.archdox.cloud.office.domain.OfficeMembership;
import com.archdox.cloud.office.infra.OfficeInvitationRepository;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.shared.MembershipRole;
import com.archdox.shared.MembershipStatus;
import com.archdox.shared.OfficeType;
import com.archdox.shared.PersonalOfficeCodeFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserAccountRepository userRepository;
    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final OfficeRepository officeRepository;
    private final OfficeMembershipRepository membershipRepository;
    private final OfficeInvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final LoginProtectionService loginProtectionService;

    public AuthService(
            UserAccountRepository userRepository,
            AuthRefreshTokenRepository refreshTokenRepository,
            OfficeRepository officeRepository,
            OfficeMembershipRepository membershipRepository,
            OfficeInvitationRepository invitationRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            LoginProtectionService loginProtectionService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.officeRepository = officeRepository;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.loginProtectionService = loginProtectionService;
    }

    @Transactional
    public AuthTokenResponse signup(SignupRequest request) {
        var email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }

        var now = OffsetDateTime.now();
        if (request.resolvedAccountType() == SignupAccountType.OFFICE) {
            return signupOfficeUser(request, email, now);
        }
        return signupPersonalUser(request, email, now);
    }

    private AuthTokenResponse signupPersonalUser(SignupRequest request, String email, OffsetDateTime now) {
        var user = userRepository.save(new UserAccount(
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim(),
                now));
        var office = officeRepository.save(new Office(
                PersonalOfficeCodeFactory.fromUserId(user.id()),
                request.name().trim() + " 개인 사무소",
                OfficeType.PERSONAL,
                "PERSONAL_FREE",
                now));
        membershipRepository.save(new OfficeMembership(user, office, MembershipRole.OWNER, now));
        return issueTokens(user, now);
    }

    private AuthTokenResponse signupOfficeUser(SignupRequest request, String email, OffsetDateTime now) {
        var officeCode = requireText(request.officeCode(), "officeCode is required for office signup");
        var invitationToken = requireText(request.invitationToken(), "invitationToken is required for office signup");
        var office = officeRepository.findByOfficeCodeIgnoreCase(officeCode)
                .orElseThrow(() -> new NotFoundException("Office code not found"));
        if (office.type() != OfficeType.OFFICE) {
            throw new ForbiddenException("Office code must reference an office workspace");
        }
        var invitation = invitationRepository.findByTokenHash(hash(invitationToken))
                .orElseThrow(() -> new NotFoundException("Office invitation not found"));
        if (!invitation.office().id().equals(office.id())) {
            throw new ForbiddenException("Office code does not match invitation");
        }
        if (invitation.isExpired(now)) {
            invitation.expire(now);
            throw new ConflictException("Office invitation has expired");
        }
        if (invitation.status() != OfficeInvitationStatus.PENDING) {
            throw new ConflictException("Office invitation is not pending");
        }
        if (!email.equals(invitation.email())) {
            throw new ForbiddenException("Invitation email does not match signup email");
        }

        var user = userRepository.save(new UserAccount(
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim(),
                now));
        membershipRepository.save(new OfficeMembership(user, invitation.office(), invitation.role(), now));
        invitation.accept(user, now);
        return issueTokens(user, now);
    }

    @Transactional
    public AuthTokenResponse login(String email, String password, String clientIp) {
        var normalizedEmail = normalizeEmail(email);
        loginProtectionService.assertLoginAllowed(normalizedEmail, clientIp);
        var user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElse(null);
        if (user == null) {
            loginProtectionService.recordFailure(normalizedEmail, clientIp, null);
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            loginProtectionService.recordFailure(normalizedEmail, clientIp, user.id());
            throw new UnauthorizedException("Invalid email or password");
        }
        loginProtectionService.recordSuccess(normalizedEmail, clientIp, user.id());
        return issueTokens(user, OffsetDateTime.now());
    }

    @Transactional
    public AuthTokenResponse refresh(String refreshToken) {
        var now = OffsetDateTime.now();
        var token = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!token.isActive(now)) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        token.revoke(now);
        return issueTokens(token.user(), now);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .ifPresent(token -> token.revoke(OffsetDateTime.now()));
    }

    @Transactional(readOnly = true)
    public MeResponse me(UserPrincipal principal) {
        var user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        var offices = membershipRepository.findByUserIdAndStatus(user.id(), MembershipStatus.ACTIVE).stream()
                .map(membership -> new OfficeSummaryResponse(
                        membership.office().id(),
                        membership.office().officeCode(),
                        membership.office().displayName(),
                        membership.office().type(),
                        membership.office().planCode(),
                        membership.role(),
                        officePermissions(membership)))
                .toList();
        return new MeResponse(user.id(), user.email(), user.name(), offices);
    }

    private OfficePermissionSummaryResponse officePermissions(OfficeMembership membership) {
        var personalOwner = membership.office().type() == OfficeType.PERSONAL && membership.role() == MembershipRole.OWNER;
        var officeAdmin = membership.office().type() != OfficeType.PERSONAL
                && (membership.role() == MembershipRole.OWNER || membership.role() == MembershipRole.ADMIN);
        var broadWorkPermission = personalOwner || officeAdmin;
        return new OfficePermissionSummaryResponse(
                officeAdmin,
                broadWorkPermission,
                officeAdmin,
                broadWorkPermission,
                broadWorkPermission,
                broadWorkPermission,
                broadWorkPermission,
                broadWorkPermission,
                broadWorkPermission,
                officeAdmin);
    }

    private AuthTokenResponse issueTokens(UserAccount user, OffsetDateTime now) {
        var rawRefreshToken = randomToken();
        var expiresAt = now.plusDays(jwtProperties.refreshTokenTtlDays());
        refreshTokenRepository.save(new AuthRefreshToken(user, hash(rawRefreshToken), expiresAt, now));
        return AuthTokenResponse.bearer(
                jwtService.createAccessToken(user),
                rawRefreshToken,
                jwtProperties.accessTokenTtlMinutes() * 60);
    }

    private String randomToken() {
        var bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash refresh token", ex);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}

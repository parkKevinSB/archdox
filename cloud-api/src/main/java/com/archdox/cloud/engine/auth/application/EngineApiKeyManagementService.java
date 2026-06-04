package com.archdox.cloud.engine.auth.application;

import com.archdox.cloud.account.infra.UserAccountRepository;
import com.archdox.cloud.engine.auth.domain.EngineApiKey;
import com.archdox.cloud.engine.auth.dto.CreateEngineApiKeyRequest;
import com.archdox.cloud.engine.auth.dto.CreateEngineApiKeyResponse;
import com.archdox.cloud.engine.auth.dto.EngineApiKeyResponse;
import com.archdox.cloud.engine.auth.infra.EngineApiKeyRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngineApiKeyManagementService {
    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_REVIEW_SESSION = "ENGINE_REVIEW_SESSION";
    public static final int DEFAULT_DAILY_REQUEST_UNIT_LIMIT = 1000;

    private final EngineApiKeyRepository repository;
    private final EngineApiKeySecretService secretService;
    private final PlatformAdminService platformAdminService;
    private final UserAccountRepository userAccountRepository;
    private final OfficeRepository officeRepository;

    public EngineApiKeyManagementService(
            EngineApiKeyRepository repository,
            EngineApiKeySecretService secretService,
            PlatformAdminService platformAdminService,
            UserAccountRepository userAccountRepository,
            OfficeRepository officeRepository
    ) {
        this.repository = repository;
        this.secretService = secretService;
        this.platformAdminService = platformAdminService;
        this.userAccountRepository = userAccountRepository;
        this.officeRepository = officeRepository;
    }

    @Transactional(readOnly = true)
    public List<EngineApiKeyResponse> keys(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id")).stream()
                .limit(200)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CreateEngineApiKeyResponse create(UserPrincipal principal, CreateEngineApiKeyRequest request) {
        platformAdminService.requirePlatformAdmin(principal);
        return issue(
                request.displayName(),
                request.ownerUserId(),
                request.officeId(),
                principal.userId(),
                request.scopes(),
                request.dailyRequestUnitLimit(),
                request.expiresAt());
    }

    @Transactional
    public CreateEngineApiKeyResponse issue(
            String displayName,
            Long ownerUserId,
            Long officeId,
            Long issuedByUserId,
            List<String> scopes,
            OffsetDateTime expiresAt
    ) {
        return issue(
                displayName,
                ownerUserId,
                officeId,
                issuedByUserId,
                scopes,
                null,
                expiresAt);
    }

    @Transactional
    public CreateEngineApiKeyResponse issue(
            String displayName,
            Long ownerUserId,
            Long officeId,
            Long issuedByUserId,
            List<String> scopes,
            Integer dailyRequestUnitLimit,
            OffsetDateTime expiresAt
    ) {
        if (ownerUserId == null) {
            throw new BadRequestException("ownerUserId is required");
        }
        if (issuedByUserId == null) {
            throw new BadRequestException("issuedByUserId is required");
        }
        if (!userAccountRepository.existsById(ownerUserId)) {
            throw new NotFoundException("Engine API key owner user not found");
        }
        if (officeId != null && !officeRepository.existsById(officeId)) {
            throw new NotFoundException("Engine API key office not found");
        }
        var now = OffsetDateTime.now();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new BadRequestException("expiresAt must be in the future");
        }
        var generated = secretService.generate();
        var entity = repository.save(new EngineApiKey(
                generated.keyId(),
                generated.keyPrefix(),
                generated.secretHash(),
                displayName(displayName),
                ownerUserId,
                officeId,
                issuedByUserId,
                scopeText(scopes),
                dailyRequestUnitLimit(dailyRequestUnitLimit),
                expiresAt,
                now));
        return new CreateEngineApiKeyResponse(toResponse(entity), generated.apiKey());
    }

    @Transactional
    public EngineApiKeyResponse revoke(UserPrincipal principal, Long apiKeyId) {
        platformAdminService.requirePlatformAdmin(principal);
        var key = repository.findById(apiKeyId)
                .orElseThrow(() -> new NotFoundException("Engine API key not found"));
        key.revoke(OffsetDateTime.now());
        return toResponse(key);
    }

    private EngineApiKeyResponse toResponse(EngineApiKey key) {
        return new EngineApiKeyResponse(
                key.id(),
                key.keyId(),
                key.keyPrefix() + "...",
                key.displayName(),
                key.ownerUserId(),
                key.officeId(),
                key.issuedByUserId(),
                scopes(key.scopes()),
                key.dailyRequestUnitLimit(),
                key.status().name(),
                key.expiresAt(),
                key.lastUsedAt(),
                key.revokedAt(),
                key.createdAt(),
                key.updatedAt());
    }

    private String displayName(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("displayName is required");
        }
        return value.trim();
    }

    private String scopeText(List<String> scopes) {
        var normalized = scopes(scopes == null || scopes.isEmpty() ? List.of(SCOPE_REVIEW_SESSION) : scopes);
        return String.join(",", normalized);
    }

    private int dailyRequestUnitLimit(Integer value) {
        if (value == null) {
            return DEFAULT_DAILY_REQUEST_UNIT_LIMIT;
        }
        if (value < 1) {
            throw new BadRequestException("dailyRequestUnitLimit must be greater than zero");
        }
        return value;
    }

    private List<String> scopes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return scopes(List.of(value.split(",")));
    }

    private List<String> scopes(List<String> values) {
        var normalized = new LinkedHashSet<String>();
        for (var value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            var scope = value.trim().toUpperCase(Locale.ROOT);
            if (!SCOPE_ALL.equals(scope) && !SCOPE_REVIEW_SESSION.equals(scope)) {
                throw new BadRequestException("Unsupported Engine API scope: " + value);
            }
            normalized.add(scope);
        }
        if (normalized.isEmpty()) {
            normalized.add(SCOPE_REVIEW_SESSION);
        }
        return List.copyOf(normalized);
    }
}

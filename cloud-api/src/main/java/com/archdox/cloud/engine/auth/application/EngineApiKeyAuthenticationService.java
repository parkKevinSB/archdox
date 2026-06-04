package com.archdox.cloud.engine.auth.application;

import com.archdox.cloud.engine.auth.domain.EngineApiKeyStatus;
import com.archdox.cloud.engine.auth.infra.EngineApiKeyRepository;
import com.archdox.cloud.global.api.UnauthorizedException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngineApiKeyAuthenticationService {
    private final EngineApiKeyRepository repository;
    private final EngineApiKeySecretService secretService;

    public EngineApiKeyAuthenticationService(
            EngineApiKeyRepository repository,
            EngineApiKeySecretService secretService
    ) {
        this.repository = repository;
        this.secretService = secretService;
    }

    @Transactional
    public EngineApiPrincipal authenticate(String rawKey) {
        EngineApiKeySecretService.ParsedKey parsed;
        try {
            parsed = secretService.parse(rawKey);
        } catch (IllegalArgumentException ex) {
            throw unauthorized();
        }
        var now = OffsetDateTime.now();
        var key = repository.findByKeyId(parsed.keyId())
                .orElseThrow(this::unauthorized);
        if (key.status() != EngineApiKeyStatus.ACTIVE || key.expired(now)) {
            throw unauthorized();
        }
        if (!secretService.matches(key.secretHash(), parsed.secretHash())) {
            throw unauthorized();
        }
        key.touchLastUsed(now);
        return new EngineApiPrincipal(
                key.id(),
                key.keyId(),
                key.ownerUserId(),
                key.officeId(),
                scopes(key.scopes()),
                key.dailyRequestUnitLimit());
    }

    private List<String> scopes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .toList();
    }

    private UnauthorizedException unauthorized() {
        return new UnauthorizedException(
                "ENGINE_API_KEY_INVALID",
                "errors.engineApi.keyInvalid",
                "Valid ArchDox Engine API key is required");
    }
}

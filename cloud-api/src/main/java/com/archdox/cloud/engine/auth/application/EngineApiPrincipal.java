package com.archdox.cloud.engine.auth.application;

import com.archdox.cloud.global.api.ForbiddenException;
import java.util.List;

public record EngineApiPrincipal(
        Long apiKeyId,
        String keyId,
        Long ownerUserId,
        Long officeId,
        List<String> scopes,
        Integer dailyRequestUnitLimit
) {
    public EngineApiPrincipal {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    public boolean hasScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return false;
        }
        var normalized = scope.trim().toUpperCase();
        return scopes.contains("ALL") || scopes.contains(normalized);
    }

    public void requireScope(String scope) {
        if (!hasScope(scope)) {
            throw new ForbiddenException(
                    "ENGINE_API_SCOPE_REQUIRED",
                    "errors.engineApi.scopeRequired",
                    "Engine API key does not have the required scope");
        }
    }
}

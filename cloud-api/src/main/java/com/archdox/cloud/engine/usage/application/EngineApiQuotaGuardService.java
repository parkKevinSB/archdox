package com.archdox.cloud.engine.usage.application;

import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.engine.usage.infra.EngineApiUsageEventRepository;
import com.archdox.cloud.global.api.TooManyRequestsException;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngineApiQuotaGuardService {
    private final EngineApiUsageEventRepository repository;

    public EngineApiQuotaGuardService(EngineApiUsageEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public void requireReviewSessionQuota(EngineApiPrincipal principal, String operation) {
        requireQuota(principal, EngineApiUsageService.CAPABILITY_REVIEW_SESSION, operation, 1);
    }

    @Transactional(readOnly = true)
    public void requireReviewSessionQuota(EngineApiPrincipal principal, String operation, int requestUnits) {
        requireQuota(
                principal,
                EngineApiUsageService.CAPABILITY_REVIEW_SESSION,
                operation,
                Math.max(1, requestUnits));
    }

    @Transactional(readOnly = true)
    public void requireQuota(
            EngineApiPrincipal principal,
            String capability,
            String operation,
            int requestUnits
    ) {
        if (principal == null) {
            return;
        }
        var limit = principal.dailyRequestUnitLimit();
        if (limit == null) {
            return;
        }
        var now = OffsetDateTime.now();
        var from = now.toLocalDate().atStartOfDay(now.getOffset()).toOffsetDateTime();
        var to = from.plusDays(1);
        var used = number(repository.sumRequestUnitsForApiKey(
                principal.apiKeyId(),
                capability,
                from,
                to));
        if (used + requestUnits > limit) {
            throw new TooManyRequestsException(
                    "ENGINE_API_DAILY_QUOTA_EXCEEDED",
                    "errors.engineApi.dailyQuotaExceeded",
                    "Engine API daily quota exceeded",
                    Map.of(
                            "apiKeyId", principal.apiKeyId(),
                            "keyId", principal.keyId(),
                            "capability", capability,
                            "operation", operation,
                            "dailyRequestUnitLimit", limit,
                            "usedRequestUnits", used,
                            "requestedUnits", requestUnits,
                            "resetsAt", to.toString()));
        }
    }

    private long number(Long value) {
        return value == null ? 0L : value;
    }
}

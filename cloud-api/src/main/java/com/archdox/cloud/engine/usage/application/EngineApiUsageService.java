package com.archdox.cloud.engine.usage.application;

import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.engine.usage.domain.EngineApiUsageEvent;
import com.archdox.cloud.engine.usage.infra.EngineApiUsageEventRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngineApiUsageService {
    public static final String CAPABILITY_REVIEW_SESSION = "ENGINE_REVIEW_SESSION";
    public static final String CAPABILITY_LEGAL_UPDATES = "LEGAL_UPDATES";
    public static final String CAPABILITY_LEGAL_SEARCH = "LEGAL_SEARCH";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DENIED = "DENIED";

    private final EngineApiUsageEventRepository repository;

    public EngineApiUsageService(EngineApiUsageEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordReviewSessionUsage(
            EngineApiPrincipal principal,
            String operation,
            String reviewSessionId,
            Map<String, Object> metadata
    ) {
        recordUsage(principal, CAPABILITY_REVIEW_SESSION, operation, reviewSessionId, 1, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUsage(
            EngineApiPrincipal principal,
            String capability,
            String operation,
            String resourceId,
            int requestUnits,
            Map<String, Object> metadata
    ) {
        recordUsage(principal, capability, operation, resourceId, STATUS_SUCCEEDED, requestUnits, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUsage(
            EngineApiPrincipal principal,
            String capability,
            String operation,
            String resourceId,
            String status,
            int requestUnits,
            Map<String, Object> metadata
    ) {
        if (principal == null) {
            return;
        }
        repository.save(new EngineApiUsageEvent(
                principal.apiKeyId(),
                principal.keyId(),
                principal.ownerUserId(),
                principal.officeId(),
                capability,
                operation,
                resourceId,
                status,
                Math.max(0, requestUnits),
                metadata == null ? Map.of() : metadata,
                OffsetDateTime.now()));
    }
}

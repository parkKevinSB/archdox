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
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";

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
        if (principal == null) {
            return;
        }
        repository.save(new EngineApiUsageEvent(
                principal.apiKeyId(),
                principal.keyId(),
                principal.ownerUserId(),
                principal.officeId(),
                CAPABILITY_REVIEW_SESSION,
                operation,
                reviewSessionId,
                STATUS_SUCCEEDED,
                1,
                metadata == null ? Map.of() : metadata,
                OffsetDateTime.now()));
    }
}

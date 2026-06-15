package com.archdox.cloud.engine.usage.application;

import com.archdox.cloud.engine.usage.domain.EngineApiUsageEvent;
import com.archdox.cloud.engine.usage.dto.EngineApiUsageEventResponse;
import com.archdox.cloud.engine.usage.dto.EngineApiUsageGroupResponse;
import com.archdox.cloud.engine.usage.dto.EngineApiUsageSummaryResponse;
import com.archdox.cloud.engine.usage.infra.EngineApiUsageEventRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngineApiUsageReadService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final EngineApiUsageEventRepository repository;
    private final PlatformAdminService platformAdminService;

    public EngineApiUsageReadService(
            EngineApiUsageEventRepository repository,
            PlatformAdminService platformAdminService
    ) {
        this.repository = repository;
        this.platformAdminService = platformAdminService;
    }

    @Transactional(readOnly = true)
    public List<EngineApiUsageEventResponse> events(
            UserPrincipal principal,
            Long apiKeyId,
            Long officeId,
            String capability,
            String operation,
            String reviewSessionId,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var range = range(from, to);
        return repository.searchPlatformUsageEvents(
                        apiKeyId,
                        officeId,
                        blankToNull(capability),
                        blankToNull(operation),
                        blankToNull(reviewSessionId),
                        range.from(),
                        range.to(),
                        PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toEventResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EngineApiUsageEventResponse> userEvents(
            UserPrincipal principal,
            Long apiKeyId,
            Long officeId,
            String capability,
            String operation,
            String reviewSessionId,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer limit
    ) {
        var range = range(from, to);
        return repository.searchUserUsageEvents(
                        principal.userId(),
                        apiKeyId,
                        officeId,
                        blankToNull(capability),
                        blankToNull(operation),
                        blankToNull(reviewSessionId),
                        range.from(),
                        range.to(),
                        PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toEventResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EngineApiUsageSummaryResponse summary(
            UserPrincipal principal,
            Long apiKeyId,
            Long officeId,
            String capability,
            String operation,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var range = range(from, to);
        var groups = toGroupResponses(repository.summarizePlatformUsage(
                        apiKeyId,
                        officeId,
                        blankToNull(capability),
                        blankToNull(operation),
                        range.from(),
                        range.to()));
        return new EngineApiUsageSummaryResponse(
                range.from(),
                range.to(),
                groups.stream().mapToLong(EngineApiUsageGroupResponse::eventCount).sum(),
                groups.stream().mapToLong(EngineApiUsageGroupResponse::requestUnits).sum(),
                groups);
    }

    @Transactional(readOnly = true)
    public EngineApiUsageSummaryResponse userSummary(
            UserPrincipal principal,
            Long apiKeyId,
            Long officeId,
            String capability,
            String operation,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        var range = range(from, to);
        var groups = toGroupResponses(repository.summarizeUserUsage(
                principal.userId(),
                apiKeyId,
                officeId,
                blankToNull(capability),
                blankToNull(operation),
                range.from(),
                range.to()));
        return new EngineApiUsageSummaryResponse(
                range.from(),
                range.to(),
                groups.stream().mapToLong(EngineApiUsageGroupResponse::eventCount).sum(),
                groups.stream().mapToLong(EngineApiUsageGroupResponse::requestUnits).sum(),
                groups);
    }

    private List<EngineApiUsageGroupResponse> toGroupResponses(
            List<EngineApiUsageEventRepository.EngineApiUsageSummaryProjection> groups
    ) {
        return groups.stream()
                .map(group -> new EngineApiUsageGroupResponse(
                        group.getApiKeyId(),
                        group.getKeyId(),
                        group.getOwnerUserId(),
                        group.getOfficeId(),
                        group.getCapability(),
                        group.getOperation(),
                        number(group.getEventCount()),
                        number(group.getRequestUnits()),
                        group.getLastCalledAt()))
                .toList();
    }

    private EngineApiUsageEventResponse toEventResponse(EngineApiUsageEvent event) {
        return new EngineApiUsageEventResponse(
                event.id(),
                event.apiKeyId(),
                event.keyId(),
                event.ownerUserId(),
                event.officeId(),
                event.capability(),
                event.operation(),
                event.reviewSessionId(),
                event.status(),
                event.requestUnits(),
                event.metadataJson(),
                event.createdAt());
    }

    private Range range(OffsetDateTime from, OffsetDateTime to) {
        var normalizedTo = to == null ? OffsetDateTime.now() : to;
        var normalizedFrom = from == null ? normalizedTo.minusDays(30) : from;
        if (!normalizedTo.isAfter(normalizedFrom)) {
            throw new BadRequestException("to must be after from");
        }
        return new Range(normalizedFrom, normalizedTo);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    private long number(Long value) {
        return value == null ? 0L : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Range(OffsetDateTime from, OffsetDateTime to) {
    }
}

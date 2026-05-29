package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiModelCallLogStatus;
import com.archdox.cloud.aipolicy.dto.AiUsageGroupResponse;
import com.archdox.cloud.aipolicy.dto.AiUsageSummaryResponse;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiUsageReadService {
    private final AiModelCallLogRepository callLogRepository;
    private final PlatformAdminService platformAdminService;

    public AiUsageReadService(
            AiModelCallLogRepository callLogRepository,
            PlatformAdminService platformAdminService
    ) {
        this.callLogRepository = callLogRepository;
        this.platformAdminService = platformAdminService;
    }

    @Transactional(readOnly = true)
    public AiUsageSummaryResponse monthlySummary(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        var now = OffsetDateTime.now();
        var from = YearMonth.from(now).atDay(1).atStartOfDay(now.getOffset()).toOffsetDateTime();
        var to = from.plusMonths(1);
        var groups = callLogRepository.usageByOfficeAndFeature(
                        from,
                        to,
                        AiModelCallLogStatus.SUCCEEDED,
                        AiModelCallLogStatus.FAILED)
                .stream()
                .map(group -> new AiUsageGroupResponse(
                        group.getOfficeId(),
                        group.getFeature(),
                        number(group.getCallCount()),
                        number(group.getSucceededCount()),
                        number(group.getFailedCount()),
                        number(group.getInputTokens()),
                        number(group.getOutputTokens()),
                        money(group.getEstimatedTotalCost())))
                .toList();
        return new AiUsageSummaryResponse(
                from,
                to,
                "MIXED",
                sum(groups, AiUsageGroupResponse::callCount),
                sum(groups, AiUsageGroupResponse::succeededCount),
                sum(groups, AiUsageGroupResponse::failedCount),
                sum(groups, AiUsageGroupResponse::inputTokens),
                sum(groups, AiUsageGroupResponse::outputTokens),
                groups.stream()
                        .map(AiUsageGroupResponse::estimatedTotalCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                groups);
    }

    private long sum(List<AiUsageGroupResponse> groups, java.util.function.ToLongFunction<AiUsageGroupResponse> getter) {
        return groups.stream().mapToLong(getter).sum();
    }

    private long number(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

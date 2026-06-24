package com.archdox.cloud.platformops.application;

import com.archdox.cloud.platformops.domain.PlatformOpsFinding;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSource;
import com.archdox.cloud.platformops.infra.PlatformOpsFindingRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFindingSeverity;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingSink;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlatformOpsDailyReportFindingSink implements FindingSink {
    private final PlatformOpsRunRepository runRepository;
    private final PlatformOpsFindingRepository findingRepository;

    public PlatformOpsDailyReportFindingSink(
            PlatformOpsRunRepository runRepository,
            PlatformOpsFindingRepository findingRepository
    ) {
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
    }

    @Override
    @Transactional
    public void accept(List<AiFinding> findings, AiHarnessRunContext ctx) {
        var run = runRepository.findByAiHarnessRunId(ctx.runId().value())
                .orElseThrow(() -> new IllegalStateException("Platform ops daily report AI run not found: " + ctx.runId().value()));
        findingRepository.deleteByRunIdAndSource(run.id(), PlatformOpsFindingSource.AI_HARNESS);
        for (var finding : findings) {
            findingRepository.save(new PlatformOpsFinding(
                    null,
                    run.id(),
                    null,
                    severity(finding.severity()),
                    PlatformOpsFindingSource.AI_HARNESS,
                    finding.code(),
                    category(finding),
                    title(finding),
                    finding.message(),
                    "PLATFORM_OPS_RUN",
                    String.valueOf(run.id()),
                    "platform-ops-daily-report",
                    String.valueOf(run.id()),
                    evidence(finding),
                    recommendation(finding),
                    OffsetDateTime.now()));
        }
    }

    private PlatformOpsFindingSeverity severity(AiFindingSeverity severity) {
        return switch (severity) {
            case INFO, LOW -> PlatformOpsFindingSeverity.INFO;
            case MEDIUM -> PlatformOpsFindingSeverity.WARN;
            case HIGH -> PlatformOpsFindingSeverity.ERROR;
            case CRITICAL -> PlatformOpsFindingSeverity.CRITICAL;
        };
    }

    private String category(AiFinding finding) {
        return finding.attributes().getOrDefault("category", "OPS_DAILY_REPORT");
    }

    private String title(AiFinding finding) {
        return finding.attributes().getOrDefault("title", finding.code());
    }

    private String recommendation(AiFinding finding) {
        var recommendation = finding.attributes().get("recommendation");
        if (recommendation != null && !recommendation.isBlank()) {
            return recommendation;
        }
        return finding.attributes().get("recommendations");
    }

    private Map<String, Object> evidence(AiFinding finding) {
        var evidence = new LinkedHashMap<String, Object>();
        if (finding.evidence() != null && !finding.evidence().isBlank()) {
            evidence.put("evidence", finding.evidence());
        }
        finding.attributes().forEach(evidence::put);
        return evidence;
    }
}

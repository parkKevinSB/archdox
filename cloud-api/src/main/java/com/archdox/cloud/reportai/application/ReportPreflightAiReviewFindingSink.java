package com.archdox.cloud.reportai.application;

import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingSink;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReportPreflightAiReviewFindingSink implements FindingSink {
    private static final String SOURCE = "AI";
    private static final String DAILY_LOG_STEP_CODE = "DAILY_LOG";
    private static final String DAILY_ITEMS_FIELD = "dailyItems";

    private final ReportPreflightReviewRunRepository runRepository;
    private final ReportPreflightReviewFindingRepository findingRepository;
    private final InspectionReportStepRepository stepRepository;
    private final PhotoRepository photoRepository;
    private final ObjectMapper objectMapper;

    public ReportPreflightAiReviewFindingSink(
            ReportPreflightReviewRunRepository runRepository,
            ReportPreflightReviewFindingRepository findingRepository,
            InspectionReportStepRepository stepRepository,
            PhotoRepository photoRepository,
            ObjectMapper objectMapper
    ) {
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
        this.stepRepository = stepRepository;
        this.photoRepository = photoRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void accept(List<AiFinding> findings, AiHarnessRunContext ctx) {
        var run = runRepository.findByHarnessRunId(ctx.runId().value())
                .orElseThrow(() -> new IllegalStateException("Report preflight AI review run not found: " + ctx.runId().value()));
        findingRepository.deleteByReviewRunIdAndSource(run.id(), SOURCE);
        for (var finding : findings) {
            if (shouldSuppressBenignDailyLogPhotoPayloadFinding(run, finding)) {
                continue;
            }
            findingRepository.save(new ReportPreflightReviewFinding(
                    run.officeId(),
                    run.id(),
                    run.reportId(),
                    SOURCE,
                    finding.code(),
                    finding.severity().name(),
                    finding.location(),
                    finding.message(),
                    finding.evidence(),
                    attributes(finding),
                    OffsetDateTime.now()));
        }
    }

    private LinkedHashMap<String, String> attributes(AiFinding finding) {
        var attributes = new LinkedHashMap<>(finding.attributes());
        attributes.put("source", SOURCE);
        attributes.put("draftOnly", "true");
        attributes.put("approvalRequired", "true");
        attributes.put("reviewMode", ReportPreflightAiHarnessFlowService.REVIEW_MODE_SOURCE_BACKED_LEGAL_DRY_RUN);
        return attributes;
    }

    private boolean shouldSuppressBenignDailyLogPhotoPayloadFinding(
            ReportPreflightReviewRun run,
            AiFinding finding
    ) {
        if (!isBenignPhotosPayloadObservation(finding)) {
            return false;
        }
        var referencedPhotoIds = dailyLogReferencedPhotoIds(run.reportId());
        if (referencedPhotoIds.isEmpty()) {
            return false;
        }
        var activePhotoIds = new HashSet<>(photoRepository
                .findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(
                        run.officeId(),
                        run.reportId(),
                        PhotoStatus.DELETED)
                .stream()
                .map(Photo::id)
                .filter(id -> id != null)
                .toList());
        return activePhotoIds.containsAll(referencedPhotoIds);
    }

    private boolean isBenignPhotosPayloadObservation(AiFinding finding) {
        var text = String.join(" ",
                        safe(finding.code()),
                        safe(finding.location()),
                        safe(finding.message()),
                        safe(finding.evidence()))
                .toUpperCase(Locale.ROOT);
        var mentionsPhotos = text.contains("PHOTOS") || text.contains("사진");
        if (!mentionsPhotos) {
            return false;
        }
        var mentionsPayloadShape = text.contains("PAYLOAD")
                || text.contains("페이로드")
                || text.contains("단계");
        var mentionsDailyLogResolvedEvidence = text.contains("DAILY_LOG")
                || text.contains("참조")
                || text.contains("정상")
                || text.contains("업로드");
        return mentionsPayloadShape && mentionsDailyLogResolvedEvidence;
    }

    private Set<Long> dailyLogReferencedPhotoIds(Long reportId) {
        return stepRepository.findByReportIdAndStepCode(reportId, DAILY_LOG_STEP_CODE)
                .map(step -> step.payloadJson())
                .map(this::dailyItemsOrPayload)
                .map(payload -> {
                    var ids = new LinkedHashSet<Long>();
                    collectPhotoIds(payload, ids);
                    return Set.copyOf(ids);
                })
                .orElse(Set.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dailyItemsOrPayload(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }
        var dailyItems = payload.get(DAILY_ITEMS_FIELD);
        if (dailyItems instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (dailyItems instanceof String json && !json.isBlank()) {
            try {
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception ignored) {
                return payload;
            }
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private void collectPhotoIds(Object value, Set<Long> ids) {
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                var key = String.valueOf(entry.getKey());
                var entryValue = entry.getValue();
                if ("photoIds".equals(key) || "photoId".equals(key)) {
                    collectPhotoIdValue(entryValue, ids);
                } else {
                    collectPhotoIds(entryValue, ids);
                }
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (var item : list) {
                collectPhotoIds(item, ids);
            }
        }
    }

    private void collectPhotoIdValue(Object value, Set<Long> ids) {
        if (value instanceof Number number) {
            ids.add(number.longValue());
            return;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                ids.add(Long.parseLong(text.trim()));
            } catch (NumberFormatException ignored) {
                // Non-numeric client photo refs are not comparable with persisted photo ids.
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (var item : list) {
                collectPhotoIdValue(item, ids);
            }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

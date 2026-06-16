package com.archdox.cloud.reportai.application;

import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ReportPhotoEvidenceStatusService {
    private static final String DAILY_LOG_STEP_CODE = "DAILY_LOG";
    private static final String PHOTOS_STEP_CODE = "PHOTOS";
    private static final String DAILY_ITEMS_FIELD = "dailyItems";

    private final InspectionReportStepRepository stepRepository;
    private final PhotoRepository photoRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final ObjectMapper objectMapper;

    public ReportPhotoEvidenceStatusService(
            InspectionReportStepRepository stepRepository,
            PhotoRepository photoRepository,
            PhotoAssetRepository photoAssetRepository,
            ObjectMapper objectMapper
    ) {
        this.stepRepository = stepRepository;
        this.photoRepository = photoRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.objectMapper = objectMapper;
    }

    public ReportPhotoEvidenceStatus evaluate(InspectionReport report) {
        return evaluate(report.officeId(), report.id());
    }

    public ReportPhotoEvidenceStatus evaluate(Long officeId, Long reportId) {
        var activePhotos = photoRepository.findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(
                officeId,
                reportId,
                PhotoStatus.DELETED);
        var activePhotoIds = activePhotos.stream()
                .map(Photo::id)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var uploadedPhotoIds = activePhotos.stream()
                .filter(photo -> photo.status() == PhotoStatus.UPLOADED)
                .map(Photo::id)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var workingUploadedPhotoIds = workingUploadedPhotoIds(activePhotoIds);
        var dailyLogReferencedPhotoIds = dailyLogReferencedPhotoIds(reportId);

        var linkedDailyLogPhotoIds = intersection(dailyLogReferencedPhotoIds, activePhotoIds);
        var missingDailyLogPhotoIds = difference(dailyLogReferencedPhotoIds, activePhotoIds);
        var pendingDailyLogPhotoIds = linkedDailyLogPhotoIds.stream()
                .filter(id -> !uploadedPhotoIds.contains(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var notWorkingUploadedDailyLogPhotoIds = linkedDailyLogPhotoIds.stream()
                .filter(id -> !workingUploadedPhotoIds.contains(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var notWorkingUploadedPhotoIds = activePhotoIds.stream()
                .filter(id -> !workingUploadedPhotoIds.contains(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var unlinkedPhotoIds = difference(activePhotoIds, dailyLogReferencedPhotoIds);

        return new ReportPhotoEvidenceStatus(
                photosStepPayloadEmpty(reportId),
                activePhotoIds.size(),
                uploadedPhotoIds.size(),
                activePhotoIds,
                uploadedPhotoIds,
                workingUploadedPhotoIds,
                dailyLogReferencedPhotoIds,
                linkedDailyLogPhotoIds,
                missingDailyLogPhotoIds,
                pendingDailyLogPhotoIds,
                notWorkingUploadedDailyLogPhotoIds,
                notWorkingUploadedPhotoIds,
                unlinkedPhotoIds);
    }

    private Set<Long> workingUploadedPhotoIds(Set<Long> photoIds) {
        if (photoIds.isEmpty()) {
            return Set.of();
        }
        return photoAssetRepository.findPhotoIdsByAssetTypeAndStatus(
                        photoIds.stream().toList(),
                        PhotoAssetType.WORKING,
                        PhotoAssetStatus.UPLOADED).stream()
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> dailyLogReferencedPhotoIds(Long reportId) {
        return stepRepository.findByReportIdAndStepCode(reportId, DAILY_LOG_STEP_CODE)
                .map(step -> step.payloadJson())
                .map(this::dailyItemsOrPayload)
                .map(payload -> {
                    var ids = new LinkedHashSet<Long>();
                    collectDailyChecklistRowPhotoIds(payload, ids);
                    return Set.copyOf(ids);
                })
                .orElse(Set.of());
    }

    private boolean photosStepPayloadEmpty(Long reportId) {
        return stepRepository.findByReportIdAndStepCode(reportId, PHOTOS_STEP_CODE)
                .map(step -> step.payloadJson() == null || step.payloadJson().isEmpty())
                .orElse(true);
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

    private void collectDailyChecklistRowPhotoIds(Map<String, Object> payload, Set<Long> ids) {
        for (Object groupValue : listValue(payload.get("groups"))) {
            var group = mapValue(groupValue);
            for (Object entryValue : listValue(group.get("entries"))) {
                var entry = mapValue(entryValue);
                for (Object rowValue : listValue(entry.get("checklistRows"))) {
                    var row = mapValue(rowValue);
                    collectPhotoIdValue(row.get("photoIds"), ids);
                }
            }
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        var result = new java.util.LinkedHashMap<String, Object>();
        map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
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
                // Client-only temporary ids cannot be matched against persisted photos.
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (var item : list) {
                collectPhotoIdValue(item, ids);
            }
        }
    }

    private Set<Long> intersection(Set<Long> left, Set<Long> right) {
        var result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return Set.copyOf(result);
    }

    private Set<Long> difference(Set<Long> left, Set<Long> right) {
        var result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return Set.copyOf(result);
    }
}

package com.archdox.cloud.reportai.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ReportPhotoEvidenceStatus(
        boolean photosStepPayloadEmpty,
        int activePhotoCount,
        int uploadedPhotoCount,
        Set<Long> activePhotoIds,
        Set<Long> uploadedPhotoIds,
        Set<Long> workingUploadedPhotoIds,
        Set<Long> dailyLogReferencedPhotoIds,
        Set<Long> linkedDailyLogPhotoIds,
        Set<Long> missingDailyLogPhotoIds,
        Set<Long> pendingDailyLogPhotoIds,
        Set<Long> notWorkingUploadedDailyLogPhotoIds,
        Set<Long> notWorkingUploadedPhotoIds,
        Set<Long> unlinkedPhotoIds
) {
    public ReportPhotoEvidenceStatus {
        activePhotoIds = Set.copyOf(activePhotoIds == null ? Set.of() : activePhotoIds);
        uploadedPhotoIds = Set.copyOf(uploadedPhotoIds == null ? Set.of() : uploadedPhotoIds);
        workingUploadedPhotoIds = Set.copyOf(workingUploadedPhotoIds == null ? Set.of() : workingUploadedPhotoIds);
        dailyLogReferencedPhotoIds = Set.copyOf(dailyLogReferencedPhotoIds == null ? Set.of() : dailyLogReferencedPhotoIds);
        linkedDailyLogPhotoIds = Set.copyOf(linkedDailyLogPhotoIds == null ? Set.of() : linkedDailyLogPhotoIds);
        missingDailyLogPhotoIds = Set.copyOf(missingDailyLogPhotoIds == null ? Set.of() : missingDailyLogPhotoIds);
        pendingDailyLogPhotoIds = Set.copyOf(pendingDailyLogPhotoIds == null ? Set.of() : pendingDailyLogPhotoIds);
        notWorkingUploadedDailyLogPhotoIds = Set.copyOf(notWorkingUploadedDailyLogPhotoIds == null
                ? Set.of()
                : notWorkingUploadedDailyLogPhotoIds);
        notWorkingUploadedPhotoIds = Set.copyOf(notWorkingUploadedPhotoIds == null ? Set.of() : notWorkingUploadedPhotoIds);
        unlinkedPhotoIds = Set.copyOf(unlinkedPhotoIds == null ? Set.of() : unlinkedPhotoIds);
    }

    public boolean allDailyLogPhotoRefsResolved() {
        return missingDailyLogPhotoIds.isEmpty()
                && pendingDailyLogPhotoIds.isEmpty()
                && notWorkingUploadedDailyLogPhotoIds.isEmpty();
    }

    public boolean hasGenerationBlockingPhotoIssue() {
        return !missingDailyLogPhotoIds.isEmpty()
                || !pendingDailyLogPhotoIds.isEmpty()
                || !notWorkingUploadedPhotoIds.isEmpty();
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("photoSourceOfTruth", "photos table plus DAILY_LOG photoIds");
        values.put("photosStepPayloadEmpty", photosStepPayloadEmpty);
        values.put("photosStepPayloadIsEvidenceSource", false);
        values.put("activePhotoCount", activePhotoCount);
        values.put("uploadedPhotoCount", uploadedPhotoCount);
        values.put("dailyLogReferencedPhotoCount", dailyLogReferencedPhotoIds.size());
        values.put("linkedDailyLogPhotoCount", linkedDailyLogPhotoIds.size());
        values.put("unlinkedPhotoCount", unlinkedPhotoIds.size());
        values.put("activePhotoIds", idList(activePhotoIds));
        values.put("uploadedPhotoIds", idList(uploadedPhotoIds));
        values.put("workingUploadedPhotoIds", idList(workingUploadedPhotoIds));
        values.put("dailyLogReferencedPhotoIds", idList(dailyLogReferencedPhotoIds));
        values.put("linkedDailyLogPhotoIds", idList(linkedDailyLogPhotoIds));
        values.put("missingDailyLogPhotoIds", idList(missingDailyLogPhotoIds));
        values.put("pendingDailyLogPhotoIds", idList(pendingDailyLogPhotoIds));
        values.put("notWorkingUploadedDailyLogPhotoIds", idList(notWorkingUploadedDailyLogPhotoIds));
        values.put("notWorkingUploadedPhotoIds", idList(notWorkingUploadedPhotoIds));
        values.put("unlinkedPhotoIds", idList(unlinkedPhotoIds));
        values.put("allDailyLogPhotoRefsResolved", allDailyLogPhotoRefsResolved());
        values.put("generationBlockingPhotoIssue", hasGenerationBlockingPhotoIssue());
        values.put("generationBlockingPhotoIssues", generationBlockingPhotoIssues());
        return Map.copyOf(values);
    }

    private List<Long> idList(Set<Long> ids) {
        return ids.stream().sorted().toList();
    }

    private List<String> generationBlockingPhotoIssues() {
        var issues = new java.util.ArrayList<String>();
        if (!missingDailyLogPhotoIds.isEmpty()) {
            issues.add("MISSING_DAILY_LOG_PHOTO_REFERENCE");
        }
        if (!pendingDailyLogPhotoIds.isEmpty()) {
            issues.add("DAILY_LOG_PHOTO_UPLOAD_PENDING");
        }
        if (!notWorkingUploadedPhotoIds.isEmpty()) {
            issues.add("PHOTO_WORKING_ASSET_NOT_READY");
        }
        return List.copyOf(issues);
    }
}

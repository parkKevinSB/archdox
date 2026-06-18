package com.archdox.document;

import java.util.Locale;
import java.util.Map;

final class PhotoDisplayTexts {
    private static final Map<String, String> CODE_LABELS = Map.ofEntries(
            Map.entry("BASIC_INFO", "기본 정보"),
            Map.entry("DAILY_LOG", "검사항목 감리 내용"),
            Map.entry("DEMOLITION_DAILY_LOG", "감리 내용"),
            Map.entry("DEMOLITION_SAFETY_CHECK", "안전점검 결과"),
            Map.entry("REPORT_OPINION", "감리 의견 체크"),
            Map.entry("REMARKS", "의견 및 특기사항"),
            Map.entry("ISSUES", "지적 및 후속 조치"),
            Map.entry("CHECKLIST", "체크리스트"),
            Map.entry("PHOTOS", "사진 증거"),
            Map.entry("SUPERVISOR_DEPLOYMENT", "감리자 배치 및 첨부 확인"),
            Map.entry("STRUCTURE_WORK", "구조공사 감리내용"),
            Map.entry("MATERIAL_CHECK", "자재 및 시험 확인"),
            Map.entry("SITE_SAFETY", "현장 안전상태"),
            Map.entry("INSTRUCTION_RESULT", "지적사항 및 처리결과"),
            Map.entry("FIELD_INVESTIGATION", "현장 조사 및 법령 기준 확인"),
            Map.entry("ENGINEER_OPINION", "관계전문기술자 의견 확인"),
            Map.entry("SUPERVISION_OPINION", "공사감리자 종합의견"),
            Map.entry("ATTACHMENTS", "첨부자료 확인"),
            Map.entry("DEMOLITION_SEQUENCE", "해체 순서 준수"),
            Map.entry("STRUCTURAL_SUPPORT", "구조 보강 상태"),
            Map.entry("FALLING_OBJECT_PREVENTION", "낙하물 방지 조치"),
            Map.entry("EQUIPMENT_ROUTE", "장비 이동 동선 확보"),
            Map.entry("WORKER_PPE", "작업자 보호구 착용"),
            Map.entry("DUST_NOISE_FIRE", "분진/소음/화재 관리"),
            Map.entry("DEBRIS_LOADING", "잔재물 적치 및 하중 관리"),
            Map.entry("SITE_ACCESS_CONTROL", "출입 통제 및 보행자 안전"),
            Map.entry("SAFETY_ISSUE", "안전상 특이사항"),
            Map.entry("WORK_SCOPE", "금일 작업범위 확인"),
            Map.entry("SUPERVISION_FOCUS", "감리 착안사항"),
            Map.entry("ISSUE_RESULT", "지적사항 처리결과"),
            Map.entry("SUPERVISOR_DEPLOYED", "감리자 배치 확인"),
            Map.entry("SAFETY_CHECK_ATTACHED", "안전점검표 첨부"),
            Map.entry("DAILY_LOG_ATTACHED", "감리업무일지 첨부"),
            Map.entry("COMPLETION_OPINION", "완료 종합의견"),
            Map.entry("FACILITY_STATUS", "시설 상태"),
            Map.entry("CRACK_CHECK", "균열 여부"),
            Map.entry("LEAK_CHECK", "누수 흔적"),
            Map.entry("EVACUATION_ROUTE", "피난통로 적치물"),
            Map.entry("WORK_STATUS", "작업 진행 상태"),
            Map.entry("MAINTENANCE_REQUIRED", "유지보수 필요"),
            Map.entry("PHOTO_REQUIRED", "사진 기록 필요"),
            Map.entry("NEXT_ACTION", "다음 조치"),
            Map.entry("RISK_NOTE", "위험 요인"),
            Map.entry("SAFETY_ACTION", "안전조치 이행상태"),
            Map.entry("SAFETY_REMARKS", "점검 특이사항")
    );

    private PhotoDisplayTexts() {
    }

    static String value(PhotoAsset photo, String source) {
        return switch (normalize(source)) {
            case "PHOTOID", "PHOTO_ID", "ID" -> valueOrBlank(photo.photoId());
            case "CHECKLISTITEMKEY", "CHECKLIST_ITEM_KEY", "STEPCODE", "STEP_CODE", "STEP" ->
                    displayCode(photo.checklistItemKey());
            case "CHECKLISTITEMLABEL", "CHECKLIST_ITEM_LABEL" ->
                    firstNonBlank(photo.caption(), displayCode(photo.checklistItemKey()));
            case "CAPTION", "DESCRIPTION", "DESC" -> valueOrBlank(photo.caption());
            case "STORAGEREF", "STORAGE_REF", "WORKINGSTORAGEREF", "WORKING_STORAGE_REF" ->
                    valueOrBlank(photo.storageRef());
            case "LAYOUTSIZE", "LAYOUT_SIZE" -> photo.layoutSize() == null ? "" : photo.layoutSize().name();
            case "MIMETYPE", "MIME_TYPE" -> valueOrBlank(photo.mimeType());
            default -> "";
        };
    }

    static String label(Map<String, String> field, PhotoAsset photo) {
        var source = field.get("source");
        var label = field.get("label");
        var normalizedSource = normalize(source);
        if (isChecklistSource(normalizedSource)) {
            return isChecklistCode(photo.checklistItemKey()) ? "항목" : translatedLabel(label, "단계");
        }
        if ("CHECKLISTITEMLABEL".equals(normalizedSource) || "CHECKLIST_ITEM_LABEL".equals(normalizedSource)) {
            return "항목";
        }
        if (isCaptionSource(normalizedSource) && captionIsChecklistLabel(photo)) {
            return "항목";
        }
        return translatedLabel(label, source);
    }

    private static boolean isChecklistSource(String normalizedSource) {
        return "CHECKLISTITEMKEY".equals(normalizedSource)
                || "CHECKLIST_ITEM_KEY".equals(normalizedSource)
                || "STEPCODE".equals(normalizedSource)
                || "STEP_CODE".equals(normalizedSource)
                || "STEP".equals(normalizedSource);
    }

    private static boolean isCaptionSource(String normalizedSource) {
        return "CAPTION".equals(normalizedSource)
                || "DESCRIPTION".equals(normalizedSource)
                || "DESC".equals(normalizedSource);
    }

    private static boolean isChecklistCode(String value) {
        return CODE_LABELS.containsKey(normalize(value));
    }

    private static boolean captionIsChecklistLabel(PhotoAsset photo) {
        var displayCode = displayCode(photo.checklistItemKey());
        return !displayCode.isBlank() && displayCode.equals(photo.caption());
    }

    private static String displayCode(String value) {
        var normalized = normalize(value);
        if (normalized.isBlank()) {
            return "";
        }
        return CODE_LABELS.getOrDefault(normalized, value);
    }

    private static String translatedLabel(String label, String fallback) {
        var text = firstNonBlank(label, fallback);
        return switch (normalize(text)) {
            case "PHOTOID", "PHOTO_ID", "PHOTO ID", "ID" -> "사진 ID";
            case "STEP", "STEPCODE", "STEP_CODE", "STEP CODE" -> "단계";
            case "CHECKLISTITEMKEY", "CHECKLIST_ITEM_KEY", "CHECKLIST ITEM", "CHECKLISTITEMLABEL",
                    "CHECKLIST_ITEM_LABEL" -> "항목";
            case "CAPTION", "DESCRIPTION", "DESC" -> "설명";
            case "STORAGEREF", "STORAGE_REF", "STORAGE", "WORKINGSTORAGEREF", "WORKING_STORAGE_REF" -> "저장 위치";
            case "MIMETYPE", "MIME_TYPE", "MIME TYPE" -> "파일 형식";
            default -> text == null ? "" : text;
        };
    }

    private static String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String valueOrBlank(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}

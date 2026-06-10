package com.archdox.cloud.aipolicy.domain;

public enum AiHarnessPolicyKey {
    LEGAL_DIGEST_ENRICHMENT(
            "법령 변경 게시글 AI 초안",
            "동기화된 법령 변경사항을 근거로 사용자용 게시글 초안, 요약, 업무 영향 메모를 생성합니다."),
    PLATFORM_OPS_DIAGNOSIS(
            "플랫폼 운영 진단 AI",
            "운영 이벤트, 작업 상태, 장애 징후 스냅샷을 기반으로 플랫폼 운영 진단 초안을 생성합니다."),
    DOCUMENT_NARRATIVE_POLISH(
            "문서 문장 다듬기 AI",
            "문서 생성 직전 감리일지 문장을 보고서 문체로 다듬는 초안을 생성합니다. 원본 업무 데이터는 수정하지 않습니다.");

    private final String displayName;
    private final String description;

    AiHarnessPolicyKey(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}

package com.archdox.cloud.aipolicy.domain;

public enum AiHarnessPolicyKey {
    LEGAL_DIGEST_ENRICHMENT(
            "법령 변경 게시글 AI 초안",
            "동기화된 법령 변경 묶음을 근거 기반 게시글 초안으로 정리합니다."),
    PLATFORM_OPS_DIAGNOSIS(
            "운영 이슈 원인 분석 AI",
            "특정 ArchDox 운영 이슈의 원인 후보와 확인할 조치 초안을 생성합니다."),
    PLATFORM_OPS_DAILY_REPORT(
            "일일 운영 리포트 AI",
            "ArchDox 런타임, Worker, MCP, AI, 운영 이벤트 근거를 바탕으로 일일 운영 리포트 초안을 생성합니다."),
    DOCUMENT_NARRATIVE_POLISH(
            "문서 출력 문장 다듬기 AI",
            "원본 구조화 업무 데이터는 유지하고 문서 출력용 문장을 다듬습니다."),
    SOURCE_BACKED_LEGAL_REVIEW(
            "법령 근거 기반 문서검토 AI",
            "ArchDox 법령 코퍼스와 업무-법령 바인딩 근거 범위 안에서 감리 문서 입력을 검토합니다.");

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

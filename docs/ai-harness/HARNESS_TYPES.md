# Harness Types

## 1. Document QA Harness

문서 생성 결과 품질을 검토한다.

입력:

- `documentJobId`
- report snapshot
- template config
- output layout
- generated HTML text
- generated artifact metadata
- photo asset metadata

검토 항목:

- 내부 변수명 노출
- placeholder 잔존
- 사진 섹션 누락
- 표 섹션 누락
- 필수 필드 누락
- 제목/문서유형 불일치
- HTML/DOCX 가독성 문제
- 한국어 라벨 누락

결과:

- `DOCUMENT_INTERNAL_KEY_EXPOSED`
- `DOCUMENT_REQUIRED_FIELD_MISSING`
- `DOCUMENT_PHOTO_SECTION_EMPTY`
- `DOCUMENT_TABLE_SECTION_EMPTY`
- `DOCUMENT_LAYOUT_READABILITY_LOW`

MVP 첫 구현 대상이다.

## 2. Legal Review Harness

법률/업무 기준 검토를 담당한다.

중요 원칙:

- AI가 법률 근거를 창작하면 안 된다.
- 법률/업무 기준은 rule set 또는 reference corpus로 관리한다.
- finding에는 basis가 있어야 한다.
- basis가 없으면 법률 finding이 아니라 일반 suggestion으로 낮춘다.

입력:

- document type
- report snapshot
- checklist answers
- photo metadata
- generated document text
- legal/business rule set

검토 항목 예:

- 해체공사 안전점검표 필수 점검 항목 누락
- 위험요인 체크 시 조치사항 누락
- 사진 증빙이 필요한 항목의 사진 누락
- 법정 서식 필수 기재사항 누락
- 날짜/감리자/현장명 같은 기본 정보 누락

결과 예:

```json
{
  "severity": "WARNING",
  "code": "LEGAL_REQUIRED_ACTION_MISSING",
  "title": "위험요인 조치사항이 누락되었습니다",
  "target": "checklist.RISK_NOTE",
  "basis": {
    "type": "RULE_SET",
    "id": "DEMOLITION_SAFETY_CHECKLIST_REQUIRED_ACTION"
  },
  "recommendation": "위험요인에 대한 조치사항을 작성하세요."
}
```

## 3. Template Onboarding Harness

새 문서 양식을 시스템에 등록하기 위한 초안을 만든다.

입력:

- reference PDF/DOCX/HWPX에서 추출한 텍스트
- 기존 template config
- 기존 standard field catalog
- 문서 유형 설명

출력:

- 필요한 입력 필드 후보
- 체크리스트 후보
- template binding 후보
- output layout 후보
- 누락 가능성이 있는 섹션

중요:

- AI가 바로 게시하지 않는다.
- 관리자가 검토하고 configuration registry에 publish한다.

## 4. Report Writing Assist Harness

사용자가 리포트를 작성하는 중에 도움을 준다.

가능한 기능:

- 문장 다듬기
- 모호한 표현 경고
- 필수 항목 누락 알림
- 사진이 필요한 항목 추천
- 과도하게 단정적인 표현 경고

주의:

- 사용자가 입력한 사실을 바꾸지 않는다.
- AI 작성문은 "제안"으로 표시한다.
- 자동저장 전에 사용자 승인이 있어야 한다.

## 5. Ops Diagnosis Harness

운영자가 시스템 이상을 분석할 때 사용한다.

입력:

- operation events
- document job 상태
- agent command 상태
- agent session/heartbeat 상태
- photo pipeline 상태
- delivery 상태
- AI call/budget 상태
- recent logs summary
- deterministic detector findings

출력:

- 이상 징후 요약
- 가능한 원인
- 확인할 데이터
- 운영자 조치 제안
- 위험도와 우선순위
- 재시도/무시/공지/수동확인 같은 다음 action 후보

운영 하네스는 ArchDox 운영 workflow의 child work로만 사용한다.

```text
PlatformOpsDiagnosisFlow
-> deterministic detectors
-> redacted ops snapshot
-> OpsDiagnosisHarness
-> platform ops findings
-> operator review
```

중요 원칙:

- AI가 DB를 직접 수정하지 않는다.
- AI가 운영 action을 자동 실행하지 않는다.
- AI 입력은 raw log가 아니라 redacted snapshot이다.
- 먼저 코드 기반 detector가 stuck job, stale Agent, 반복 실패, 비용 급증,
  보안 이벤트 급증 같은 확정 가능한 문제를 잡는다.
- AI는 원인 추정, 상관관계 요약, 조치 후보 제안에 사용한다.
- 사람이 승인하기 전까지 결과는 finding/suggestion이다.

초기 구현은 `cloud-api` 안의 `platformops` 경계에서 시작한다. 나중에
운영 분석이 무거워지면 `platform-ops-worker` 또는 `ArchDox Ops Agent`로
분리할 수 있다.
## Current Implementation Note

- `PlatformOpsDetectionFlow` creates deterministic incidents and findings.
- `PlatformOpsDiagnosisFlow` can be submitted for a selected incident.
- The diagnosis flow first builds a redacted snapshot and records a
  `SYSTEM_DIAGNOSIS` finding.
- `OpsDiagnosisHarness` V1 is implemented as optional child work. It runs only
  when platform ops AI diagnosis is enabled and a provider/model are configured.
- AI output is stored as `AI_HARNESS` findings. The harness may suggest causes
  and next checks, but it must not repair data or send Agent commands.

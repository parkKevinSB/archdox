# Implementation Roadmap

## Phase AI-0: 문서화와 경계 확정

목표:

- AI Harness 개념과 모듈 경계 확정
- Flower/Bloom 사용 원칙 확정
- 첫 구현 범위 확정

산출물:

- `docs/ai-harness/*`

## Phase AI-1: flower-ai-harness 연동

목표:

- provider에 독립적인 하네스 공통 모델을 ArchDox에 연동

구현 후보:

- `AiHarnessType`
- `AiHarnessStatus`
- `AiFinding`
- `AiFindingSeverity`
- `AiFindingEvidence`
- `AiModelGateway`
- `AiModelRequest`
- `AiModelResponse`
- `AiOutputSchemaValidator`
- `AiRefinePolicy`

주의:

- JPA를 넣지 않는다.
- OpenAI/Ollama 구현을 넣지 않는다.
- 문서 도메인 전용 코드를 넣지 않는다.

## Phase AI-2: archdox-ai-harness 모듈

목표:

- ArchDox 업무 도메인용 하네스 구조 작성

구현 후보:

- `DocumentQaHarnessFactory`
- `ReportPreflightHarnessFactory`
- `OpsDiagnosisHarnessFactory`
- `DocumentQaPromptBuilder`
- `DocumentFindingNormalizer`
- `DocumentDeterministicCheckService`

초기 flow:

```text
load-context
run-deterministic-checks
build-prompt
execute-ai-review
normalize-findings
validate-findings
persist-findings
publish-completed
```

원칙:

- 프롬프트/스키마/finding extractor는 `archdox-ai-harness`에 둔다.
- REST, 권한, DB 저장, operation event 기록은 `cloud-api`에 둔다.
- 운영 진단처럼 문서가 아닌 AI 업무도 ArchDox 전용이면 이 모듈에 둔다.
- 범용 observer/trace/cost 기능은 ArchDox에서 충분히 검증된 뒤에만
  `flower-ai-harness-*`로 추출한다.

## Phase AI-3: cloud-api 통합

목표:

- cloud-api 내부 Flower worker에서 Document QA Harness 실행

구현 후보:

- `AiHarnessRun` JPA entity
- `AiHarnessFinding` JPA entity
- `AiHarnessRunRepository`
- `AiHarnessFindingRepository`
- Spring AI `ChatClient` -> `AiModelGateway` adapter through
  `flower-ai-harness-spring-ai`
- `GET /api/v1/document-jobs/{id}/ai-findings`
- `POST /api/v1/document-jobs/{id}/ai-review`

자동 trigger:

- document job `GENERATED` 후 Document QA Harness submit

수동 trigger:

- 사용자 또는 관리자 재검토 버튼

## Phase AI-O1: DocumentReviewFlow Foundation

목표:

- 현재 REST에서 단일 `DocumentQaHarness`를 바로 submit하는 구조를
  ArchDox business-level `DocumentReviewFlow`로 올린다.
- 하나의 flow는 "AI 호출 한 번"이 아니라 "문서 검토 업무 한 건"을 책임진다.
- `DocumentReviewFlow`는 하네스를 child work로 실행하고, DB 상태를 보며
  비동기로 진행한다.

초기 step:

```text
load-review-context
submit-document-qa-harness
await-document-qa-harness
summarize-document-qa-result
complete-document-review
```

원칙:

- `flower-ai-harness` 내부 step은 AI 호출 1건의 안전한 실행 단위다.
- ArchDox Flower flow는 어떤 하네스를 언제 실행하고, 결과를 어떻게 업무
  상태로 반영할지 결정한다.
- Controller는 flow submit과 현재 상태 응답만 담당한다.
- AI provider 호출은 Spring AI와 `flower-ai-harness-spring-ai`가 담당한다.

상세 설계:

- `docs/ai-harness/ARCHDOX_AI_ORCHESTRATION_FLOW.md`

## Phase AI-3B: Office AI Policy Execution Gate

Status: implemented foundation.

What changed:

- Document AI review resolves an office-level AI execution plan before the
  harness flow is created.
- The plan checks global review enablement, office `aiEnabled`, feature-level
  `documentReviewAiEnabled`, assigned provider credential, provider status, and
  default model.
- The resolved `ModelId` is `{providerCode}:{defaultModel}` and is passed to the
  child `DocumentQaHarness` through `AiHarnessFlowFactory.RunOverrides`.
- Cloud API has an ArchDox infrastructure `AiModelGateway` backed by
  `ai_provider_credentials`.

Supported V1 execution targets:

- `OPENAI`
- `CUSTOM_HTTP` for OpenAI-compatible gateways
- `OLLAMA`

`GEMINI` and `ANTHROPIC` remain registered provider types but are not directly
executable until provider-specific adapters are added.

## Phase AI-4: Legal Review Harness V1

목표:

- 법률/업무 기준 기반 검토를 추가

선행 필요:

- 문서유형별 rule set
- 기준 문서/reference evidence 관리
- finding basis 필수화

초기 대상:

- 해체공사 안전점검표
- 공사감리일지
- 감리보고서

## Phase AI-5: Template Onboarding Harness

목표:

- reference 문서에서 template/config 초안 생성

출력:

- field 후보
- checklist 후보
- binding 후보
- output layout 후보

반영:

- 관리자 승인 후 configuration registry에 publish

## Phase AI-6: ai-harness-worker 분리

조건:

- AI 검토 작업량이 많아짐
- API 서버 응답성에 영향 발생
- provider별 rate limit/cost 관리 필요
- 긴 작업을 별도 배포 단위로 운영할 필요 발생

구조:

```text
cloud-api
  - harness run 생성
  - 상태 조회

ai-harness-worker
  - Flower runtime
  - AI provider 호출
  - finding 저장
```

초기부터 이 단계로 가지 않는다.

## Phase AI-OPS-1: Platform Ops Workflow Foundation

목표:

- 운영 진단도 ArchDox workflow로 모델링한다.
- `flower-ai-harness`를 직접 운영 주체로 쓰지 않고, ArchDox
  `PlatformOpsDiagnosisFlow` 안의 child work로 사용한다.

구현 후보:

- `cloud-api` 안에 `platformops` package boundary 추가
- 기존 stuck detection 로직을 detector 단위로 정리
- redacted ops snapshot DTO 정의
- ops run/finding/incident 최소 저장 구조 검토
- platform admin read API에 incident/run/finding 조회 추가

초기 flow:

```text
load-ops-context
run-deterministic-detectors
build-redacted-ops-snapshot
decide-whether-ai-is-needed
submit-ops-diagnosis-harness
await-ops-diagnosis-harness
persist-ops-findings
complete-or-await-operator-action
```

원칙:

- deterministic detector가 먼저 실행된다.
- AI에는 raw log가 아니라 redacted snapshot만 보낸다.
- AI finding은 자동 복구가 아니라 운영자 판단 자료다.
- 복구 action은 platform admin 승인 이후 별도 flow로 실행한다.

상세 설계:

- `docs/architecture/OPERATIONS_AND_ADMIN.md`
- `docs/ai-harness/HARNESS_TYPES.md`

## Phase AI-OPS-2: Ops Diagnosis Harness V1

목표:

- operation events, stuck state, summarized logs, AI usage/cost anomalies를
  바탕으로 운영 진단 finding을 만든다.

구현 후보:

- `OpsDiagnosisHarness`
- `OpsDiagnosisPromptBuilder`
- `OpsDiagnosisOutputSchema`
- `OpsFindingNormalizer`
- `OpsSnapshotRedactor`

출력:

- 이상 징후 요약
- 가능한 원인
- 근거 데이터
- 위험도
- 확인할 다음 데이터
- 추천 운영 action 후보

금지:

- AI가 DB를 직접 수정
- AI가 Agent command를 직접 재전송
- AI가 로그/파일/토큰 원문을 provider로 전송
- AI가 platform admin 승인 없이 복구 action 실행

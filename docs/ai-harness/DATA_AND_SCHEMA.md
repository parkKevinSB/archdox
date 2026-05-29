# Data And Schema

## 기본 저장 단위

AI Harness 결과는 대화 로그가 아니라 검토 run과 finding으로 저장한다.

```text
ai_harness_runs
ai_harness_steps
ai_harness_findings
ai_harness_evidence
```

초기 구현에서는 테이블을 한 번에 모두 만들 필요는 없다. 다만 schema는
처음부터 이 방향을 기준으로 설계한다.

## AiHarnessRun

```text
id
office_id
harness_type
target_type
target_id
status
provider
model
prompt_version
input_hash
attempt_count
refine_count
requested_by
requested_at
started_at
completed_at
failed_at
failure_code
failure_message
created_at
updated_at
```

`target_type` 예:

- `DOCUMENT_JOB`
- `INSPECTION_REPORT`
- `DOCUMENT_TEMPLATE_REVISION`
- `OPERATION_EVENT_WINDOW`

`status` 예:

- `REQUESTED`
- `RUNNING`
- `WAITING_RETRY`
- `REFINING`
- `COMPLETED`
- `COMPLETED_WITH_WARNINGS`
- `FAILED`
- `CANCELLED`

## AiHarnessFinding

```text
id
run_id
office_id
severity
code
title
message
target_path
basis_type
basis_id
recommendation
confidence
status
created_at
```

`severity`:

- `INFO`
- `WARNING`
- `ERROR`
- `BLOCKER`

`status`:

- `OPEN`
- `ACKNOWLEDGED`
- `RESOLVED`
- `DISMISSED`

finding은 UI와 admin이 공통으로 읽을 수 있어야 한다.

## Evidence

법률검토와 문서 QA에서는 근거가 중요하다.

```text
source_type
source_id
source_path
excerpt
page_no
field_path
rule_code
```

`source_type` 예:

- `REPORT_SNAPSHOT`
- `GENERATED_HTML`
- `TEMPLATE_CONFIG`
- `OUTPUT_LAYOUT`
- `PHOTO_ASSET`
- `LEGAL_RULE`
- `REFERENCE_DOCUMENT`

법률 finding은 최소 하나의 `LEGAL_RULE` 또는 `REFERENCE_DOCUMENT` evidence를
가져야 한다.

## AI Output Schema

모델 provider가 무엇이든 출력 schema는 고정한다.

```json
{
  "summary": "string",
  "findings": [
    {
      "severity": "WARNING",
      "code": "DOCUMENT_PHOTO_SECTION_EMPTY",
      "title": "사진 섹션이 비어 있습니다",
      "message": "체크리스트에는 사진 2장이 연결되어 있지만 문서 출력에는 사진이 없습니다.",
      "targetPath": "sections.checklistPhotoSection",
      "basis": {
        "type": "GENERATED_HTML",
        "sourceId": "documentJob:123",
        "excerpt": "체크 항목별 사진"
      },
      "recommendation": "사진 asset binding과 output layout의 photo section을 확인하세요.",
      "confidence": 0.86
    }
  ]
}
```

허용하지 않는 응답:

- 자유 텍스트만 있는 응답
- severity가 없는 finding
- code가 없는 finding
- 법률 finding인데 basis가 없는 응답
- "아마도", "추측"만 있고 target이 없는 응답

## Prompt Version

prompt는 코드처럼 버전이 필요하다.

```text
document-qa.v1
legal-review.demolition-safety.v1
template-onboarding.korean-form.v1
```

run에는 `prompt_version`을 저장한다. 나중에 같은 문서를 다른 prompt로 다시
검토했을 때 결과 비교가 가능해야 한다.

## Privacy

AI provider로 보낼 수 있는 데이터는 정책으로 제한한다.

기본 원칙:

- password, token, secret, 주민번호 같은 민감정보는 전송 금지
- 원본 사진 파일은 기본적으로 전송하지 않음
- 필요한 경우 thumbnail/working image 또는 metadata만 사용
- 법률검토는 가능한 텍스트와 metadata 중심
- provider 요청/응답 원문 저장은 기본 OFF

운영 옵션:

```text
storePrompt: false
storeRawResponse: false
redactPersonalData: true
allowImageUploadToAiProvider: false
```


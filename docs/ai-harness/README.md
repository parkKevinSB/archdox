# ArchDox AI Harness

이 폴더는 ArchDox의 AI Harness 설계를 독립적으로 정리한다.

AI Harness는 AI를 문서 생성 엔진 안에 섞는 기능이 아니다. AI를 Flower
기반의 통제된 workflow 안에서 실행하고, 결과를 검증 가능한 finding으로
남기는 실행 틀이다.

## 핵심 방향

ArchDox의 기존 기준은 유지한다.

```text
report snapshot
+ template config
+ output layout
-> document-engine
-> HTML / DOCX / PDF
```

AI Harness는 이 흐름의 앞뒤에 붙는다.

```text
문서 생성 전
-> 입력 누락, 법률/업무 기준, 사진 증빙 조건 검토

문서 생성 후
-> 생성 결과 품질, 내부 변수 노출, 사진/표 누락, 법정 서식 충족 여부 검토
```

AI는 사실 데이터를 직접 바꾸지 않는다.

AI가 할 수 있는 일:

- 문제 발견
- 근거 제시
- 수정 제안
- 초안 생성
- 품질/법률/업무 기준 검토 보조

AI가 하면 안 되는 일:

- 사용자가 입력하지 않은 현장 사실을 임의로 작성
- 사진이 없는데 있는 것처럼 꾸미기
- 법률 근거를 창작
- 검토 결과를 사람 승인 없이 업무 데이터에 자동 반영

## 문서 목록

- [ARCHITECTURE.md](ARCHITECTURE.md): 모듈/프로세스/의존성 구조
- [ARCHDOX_AI_ORCHESTRATION_FLOW.md](ARCHDOX_AI_ORCHESTRATION_FLOW.md): ArchDox business-level AI orchestration flow boundary and phases
- [FLOWER_BLOOM_RUNTIME.md](FLOWER_BLOOM_RUNTIME.md): Flower/Bloom 실행 방식
- [HARNESS_TYPES.md](HARNESS_TYPES.md): 문서 QA, 법률검토, 템플릿 온보딩 등 하네스 유형
- [DATA_AND_SCHEMA.md](DATA_AND_SCHEMA.md): run, step, finding, evidence 데이터 구조
- [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md): 구현 단계

## MVP 결론

처음에는 별도 프로세스를 만들지 않는다.

```text
코드 구조: flower-ai-harness 라이브러리 + archdox-ai-harness Gradle 모듈
실행 구조: cloud-api 프로세스 내부
미래 구조: ai-harness-worker 별도 프로세스로 분리 가능
```

첫 구현 대상은 `Document QA Harness`, `Report Preflight Harness`,
`Ops Diagnosis Harness`다. 문서 생성 품질, 생성 전 검토, 운영 진단은
모두 ArchDox 업무 지식이 필요하므로 `archdox-ai-harness`에 둔다.

나중에 observer, trace export, provider health, fake provider fixture 같은
일부 기능이 다른 프로젝트에서도 반복되면 `flower-ai-harness-*` 범용
모듈로 추출할 수 있다. 다만 MVP에서는 ArchDox 전용 모듈 안에서 먼저
검증한다.

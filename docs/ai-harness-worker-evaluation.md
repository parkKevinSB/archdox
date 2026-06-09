# ArchDox AI Harness / Worker Evaluation

이 문서는 ArchDox에서 AI 하네스와 Worker 실행층을 제어 시스템 관점으로 검증하는 기준을 정리한다.

## 원칙

`flower-ai-harness`는 당장 수정하지 않는다.

먼저 ArchDox 안에서 평가셋과 러너를 만들어 실제 업무 케이스로 검증한다. 반복되는 패턴이 생기고 ArchDox에 종속되지 않는다고 판단될 때만 `flower-ai-harness` 또는 향후 `flower-agent-runtime`으로 일반화한다.

## 검증 대상

검증은 실행층을 분리해서 본다. Admin의 `AI 관리 > 개요` 점수판은 아래 그룹을 합산한다.

```text
AI Harness baseline: 12
Worker Control baseline: 10
MCP / Engine boundary: 9
Legal sync / digest: 8
Worker policy / governance: 8
Total: 47
```

### AI Harness

AI 하네스는 판단과 초안 생성을 담당한다.

검증 항목:

- 결과가 typed DTO로 파싱되는가
- 잘못된 JSON이 refine/retry 되는가
- 결과 status가 기대와 맞는가
- source-backed 법령 근거가 입력 범위를 벗어나지 않는가
- Worker planner가 available action 밖의 action을 제안하지 않는가
- Report preflight finding이 code/severity/message를 갖는가

현재 평가 테스트:

```text
archdox-ai-harness/src/test/java/com/archdox/evaluation/ArchDoxHarnessEvaluationSuiteTest.java
```

현재 포함 케이스:

- Legal digest: publishable supervision article update
- Legal digest: long form attachment requires human review
- Legal digest: deleted supervision checklist item requires human review
- Legal digest: multi article update keeps all source keys
- Conversation planner: create site request proposes only available action
- Conversation planner: document generation without report context asks clarification
- Conversation planner: ready report generation proposes available generation action
- Conversation planner: casual acknowledgement produces no action
- Report preflight: uploaded photo evidence passes
- Report preflight: deterministic missing photo remains visible
- Report preflight: signature evidence missing fails generation gate
- Report preflight: legal evidence context missing remains warning

### ArchDox Worker

Worker는 AI 또는 사용자가 제안한 action의 실행 권한을 통제한다.

검증 항목:

- unknown action은 policy 전에 reject 되는가
- policy denied면 executor가 실행되지 않는가
- approval required면 pending 상태로 멈추는가
- run-control cancel이면 policy 통과 후 실행 직전에 멈추는가
- executor failure가 실패 결과로 격리되는가
- trace event가 상태별로 분리되어 남는가

현재 평가 테스트:

```text
archdox-worker/src/test/java/com/archdox/worker/evaluation/ArchDoxWorkerControlEvaluationSuiteTest.java
```

현재 포함 케이스:

- allowed action executes once
- unknown action is rejected before policy
- policy denial blocks execution
- approval requirement blocks execution
- run control cancellation blocks after policy
- executor failure is isolated as failed result
- executor rejected result remains rejected trace
- executor pending approval result remains approval trace
- executor cancelled result is separated from rejection
- executor exception is caught as failed result

### MCP / Engine Boundary

MCP와 Engine API는 외부 Agent가 쓰는 경계다.

검증 항목:

- Engine API key 없이는 MCP 호출이 막히는가
- JSON-RPC initialize/tools/list/tools/call 응답 계약이 유지되는가
- invalid params, unknown tool, scope 부족, quota 초과가 구분되는가
- `get_legal_updates`, `search_law`, `get_law_article` usage가 capability별로 기록되는가
- Engine response가 typed DTO 계약을 유지하는가

대표 테스트:

```text
cloud-api/src/test/java/com/archdox/cloud/engine/mcp/McpGatewayIntegrationTest.java
cloud-api/src/test/java/com/archdox/cloud/engine/application/ArchDoxEngineServiceTest.java
```

### Legal Sync / Digest

법령 동기화와 게시글 초안은 원문 corpus를 바꾸지 않는 방향으로 검증한다.

검증 항목:

- fake legal source가 사용자/운영 목록에 섞이지 않는가
- deterministic digest refresh가 AI digest와 missing act를 건너뛰는가
- added/modified/removed article diff가 분리되는가
- AI draft worker가 dry-run으로만 실행되는가
- 승인 전에는 deterministic digest가 바뀌지 않는가
- 승인 후 apply할 때만 published digest가 AI 초안으로 바뀌는가

대표 테스트:

```text
cloud-api/src/test/java/com/archdox/cloud/legal/application/LegalPlatformAdminServiceTest.java
cloud-api/src/test/java/com/archdox/cloud/legal/application/LegalDigestEnrichmentArchDoxWorkerActionExecutorTest.java
cloud-api/src/test/java/com/archdox/cloud/legal/application/LegalDiffServiceTest.java
cloud-api/src/test/java/com/archdox/cloud/legal/application/LegalUpdateReadServiceTest.java
```

### Worker Policy / Governance

Worker 정책과 관측은 실행 허용/차단/승인/취소를 숫자로 분리해서 본다.

검증 항목:

- disabled action, disallowed source, missing context가 차단되는가
- approval-required action이 승인 전에는 막히고 승인 후에는 열리는가
- 문서 생성 전 preflight 통과 조건이 지켜지는가
- governance summary가 cancel, failure, catch, approval rate를 분리하는가

대표 테스트:

```text
cloud-api/src/test/java/com/archdox/cloud/worker/ArchDoxWorkerActionPolicyGateTest.java
cloud-api/src/test/java/com/archdox/cloud/worker/governance/application/WorkerGovernanceReadServiceTest.java
```

## 실행

```bash
./gradlew :archdox-ai-harness:test --tests com.archdox.evaluation.ArchDoxHarnessEvaluationSuiteTest
./gradlew :archdox-worker:test --tests com.archdox.worker.evaluation.ArchDoxWorkerControlEvaluationSuiteTest
./gradlew :cloud-api:test --tests com.archdox.cloud.aiharness.application.AiWorkerEvaluationReadServiceTest
```

## 다음 단계

1. static baseline을 실제 Gradle/JUnit report에서 읽는 runtime scorecard로 바꾼다.
2. fake provider 평가와 real model 평가를 분리한다.
3. real model 평가는 CI hard gate가 아니라 Admin 또는 수동 runner에서 시작한다.
4. 평가 결과에 pass/fail뿐 아니라 latency, attempt count, validation status, finding count, cost estimate를 붙인다.
5. 반복되는 ArchDox 전용 검증 규칙은 `archdox-ai-harness` validator 또는 cloud-api post-check로 승격한다.
6. ArchDox와 무관한 반복 패턴만 `flower-ai-harness` 또는 `flower-agent-runtime`으로 추출한다.

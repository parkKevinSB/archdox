# ArchDox AI Harness / Worker Evaluation

이 문서는 ArchDox에서 AI 하네스와 Worker 실행층을 제어 시스템 관점으로 검증하는 기준을 정리한다.

## 원칙

`flower-ai-harness`는 당장 수정하지 않는다.

먼저 ArchDox 안에서 평가셋과 러너를 만들어 실제 업무 케이스로 검증한다. 반복되는 패턴이 생기고 ArchDox에 종속되지 않는다고 판단될 때만 `flower-ai-harness` 또는 향후 `flower-agent-runtime`으로 일반화한다.

## 검증 대상

검증은 두 층을 분리해서 본다.

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
- Conversation planner: create site request proposes only available action
- Conversation planner: document generation without report context asks clarification
- Report preflight: uploaded photo evidence passes
- Report preflight: deterministic missing photo remains visible

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

## 실행

```bash
./gradlew :archdox-ai-harness:test --tests com.archdox.evaluation.ArchDoxHarnessEvaluationSuiteTest
./gradlew :archdox-worker:test --tests com.archdox.worker.evaluation.ArchDoxWorkerControlEvaluationSuiteTest
```

## 다음 단계

1. 평가 케이스를 20개 안팎으로 확장한다.
2. fake provider 평가와 real model 평가를 분리한다.
3. real model 평가는 CI hard gate가 아니라 Admin 또는 수동 runner에서 시작한다.
4. 평가 결과에 pass/fail뿐 아니라 latency, attempt count, validation status, finding count, cost estimate를 붙인다.
5. 반복되는 ArchDox 전용 검증 규칙은 `archdox-ai-harness` validator 또는 cloud-api post-check로 승격한다.
6. ArchDox와 무관한 반복 패턴만 `flower-ai-harness` 또는 `flower-agent-runtime`으로 추출한다.

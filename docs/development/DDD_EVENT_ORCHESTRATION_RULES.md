# DDD Event And Orchestration Rules

This document records how ArchDox should use DDD, Bloom, and Flower.

## Core Direction

ArchDox should be implemented as a DDD-style modular system:

- ArchDox is a document workflow orchestration platform, not a narrow document
  generation utility.
- Domain model owns business state and invariants.
- Application services coordinate use cases and transactions.
- Infrastructure adapters handle database, storage, WebSocket, HTTP, and file IO.
- Controllers stay thin and do not contain business decisions.
- Events are explicit Java records with meaningful names.

Avoid hiding important behavior inside generic service methods when the behavior
is really a domain event or a workflow step.

## Bloom Usage

Use Bloom for in-process runtime events inside a module or bounded context.

Bloom is appropriate for:

- domain events inside one JVM
- module-internal decoupling
- notifying projections, handlers, and in-memory adapters
- feature-local event buses
- tests that need deterministic event dispatch

Bloom is not appropriate for:

- durable queues
- cross-process delivery guarantees
- distributed workflow retries
- replacing database transactions

Bloom event rules:

1. Events should be immutable Java records.
2. Event names should describe business facts, not technical actions.
   - Good: `PhotoUploadConfirmed`, `PhotoPickupFailed`
   - Avoid: `RunRetryTask`, `CallServiceEvent`
3. Subscribe to exact event classes.
4. Keep handlers short. Long work must move to an application service,
   Flower flow, executor, or external worker boundary.
5. A module may expose events, but it must not expose internal entity mutation
   methods just so another module can change state directly.

## Flower Usage

Use Flower for orchestration that has phases, waiting, retry, timeout, or
step-by-step progression.

Flower is appropriate for:

- `PHOTO_PICKUP` retry/backoff and timeout handling
- image derivative generation such as original -> working -> thumbnail
- document generation workflows
- document delivery workflows
- long-lived process progression that needs to keep waiting across worker ticks

## Implemented ArchDox Flows

- Spring-level Bloom handlers must use `bloom-spring` `@Subscribe`.
  Do not create manual `eventBus.subscribe(...)` subscriber classes unless a
  non-Spring runtime object truly needs a shorter lifecycle.
- `photo-derivative-generation`
  - Trigger: `PhotoUploadConfirmed`
  - Worker: `document-io`
  - Steps: `prepare-source`, `generate-working`, `generate-thumbnail`,
    `finalize`
  - Retry cursor: stepNo `0` submit, `10` observe, `20` backoff
- `document-generation`
  - Trigger: REST entrypoint directly submits the flow after creating
    `document_jobs`
  - Flow input: `DocumentGenerationRequested`
  - Worker: `document-io`
  - Steps: `validate-job`, `render-archdox-agent-document`
  - Retry cursor: stepNo `0` submit, `10` observe, `20` backoff
  - Completion event: `DocumentGeneratedEvent`
  - Retry exhaustion event: `DocumentGenerationFailedEvent`
- ArchDox Agent command lifecycles
- any flow where `stepNo` makes the state easier to inspect

Flower step rules:

1. Each flow must have a stable `flowType` and `flowKey`.
2. Each step must have a stable readable step id.
3. Use `stepNo` for small internal cursors inside one step.
4. Do not block a worker tick with sleep, polling, long network calls, or
   database loops.
5. Start external work in `onEnter`, observe completion through Bloom/Flower
   events, stored state, or timeout, then return `stay()` until ready.
6. Steps orchestrate. Domain rules remain in domain/application services.
7. Tests for Flower flows should prefer deterministic `attach()` and
   `tickOnce()` style.
8. Keep production Step classes in a dedicated `*.flow.step` package.
9. Do not define multiple production Step classes as inner classes inside a
   Flow factory.
10. Avoid generic abstract Step parents unless the shared behavior is truly a
    stable orchestration concept. Do not introduce an abstract Step just to
    remove a few lines of try/catch.
11. A Flower flow is allowed to be long-lived. It does not need to finish during
    the request or shortly after creation.
12. Do not introduce Spring `@Scheduled`, cron jobs, polling loops, or ad-hoc
    background schedulers for orchestration unless the user explicitly confirms
    that a scheduler is needed.

## Flower Worker Lane Rules

Flower workers are execution lanes, not feature names. Do not add a new worker
just because a new flow, AI harness, or button exists.

Create or keep a separate worker only when the flow has a different execution
risk profile:

- it still contains `submitAndAwait`, polling sleep, blocking external IO, or
  other tick-blocking behavior;
- it can wait on a slow external system and must not delay unrelated flows;
- it has a different operational priority, retry cadence, or isolation need;
- it runs CPU/memory-heavy work that is not already moved to a bounded
  executor or external runtime;
- it needs a different concurrency/backpressure policy.

Prefer a shared lane when flows have the same execution character:

- the step starts external work and then observes completion by event, stored
  state, timeout, or `CompletableFuture` status;
- waiting is represented with `stepNo`, `ctx.startTimeout(...)`, and
  `StepResult.stay()`;
- heavy work is delegated to a bounded executor, ArchDox Agent, or another
  explicit runtime boundary;
- a delay/failure in one flow should not consume the worker thread for the
  duration of that delay.

Current lane direction:

| Lane | Owns | Rule |
| --- | --- | --- |
| `document-io` | photo derivative, photo pickup, document generation, document delivery orchestration | Shared because these flows delegate heavy work to executors or ArchDox Agent commands and wait by state/event. |
| `monitoring` | long-lived monitor/control flows | Shared for lightweight periodic control loops that use `stepNo` and timeouts. |
| `document-review` | deterministic review orchestration | May absorb more review flows once they do not block worker ticks. |
| `ai-harness` | AI child harness flows such as document AI review, report preflight AI review, source-backed legal review AI, platform ops diagnosis AI, document narrative polish AI, worker chat planner AI, and legal digest draft AI. | Shared because these flows are bounded AI execution units and provider calls are protected by the model gateway bulkhead. Workflow type names such as `document-ai-review` remain separate from the worker lane name. Narrative polish, worker chat planner, and legal digest draft may still wait from their caller, but they no longer own dedicated Flower worker lanes. |
| `legal-sync` | Law Open Data sync | Keep isolated because Law Open Data fetch is slow external I/O. The fetch step must submit source fetch work to the bounded `legal-sync-fetch-*` executor, move to `stepNo=10`, and observe completion on later ticks instead of performing HTTP/retry waits inside the Flower tick. The executor is configured by `archdox.legal.sync.fetch-executor-threads` and `archdox.legal.sync.fetch-executor-queue-capacity`. |
| `archdox-worker` | controlled SaaS action execution | A worker action that waits on AI, external I/O, or a child flow must implement `ArchDoxWorkerAsyncActionExecutor`. The generic execution step submits the action future, moves to a waiting `stepNo`, and observes completion on later ticks. Do not hide long waits inside the synchronous `execute()` method. |

AI harness model calls must not use the JVM common pool as the concurrency
boundary. In Cloud API, `ArchDoxProviderAiModelGateway` runs provider calls
through the bounded `ai-model-gateway-*` executor, configured by
`archdox.ai.model-gateway.execution.threads` and
`archdox.ai.model-gateway.execution.queue-capacity`. If that queue is full, the
call fails as an observable gateway overload instead of silently consuming
unbounded threads.

ArchDox worker async actions run their blocking bridge code through the bounded
`archdox-worker-action-*` executor, configured by
`archdox.worker.action-executor-threads` and
`archdox.worker.action-executor-queue-capacity`. This executor is not a new
Flower worker lane; it is a backpressure boundary for action code that still
needs to wait on a child runtime while the Flower worker keeps ticking.

When a new feature needs orchestration, first choose an existing lane by
execution character. A new worker name requires an explicit isolation reason in
code review and the relevant architecture document.

## Boundary Between Bloom And Flower

Use this rule:

```text
Bloom = event notification inside a runtime/module
Flower = stateful orchestration across time, retries, and steps
```

Flower may use Bloom through the Flower-Bloom adapter. In that case, the same
runtime event model is visible both to ordinary module handlers and to workflow
steps.

## ArchDox Implementation Rule

For future work:

1. Simple CRUD can remain a direct application-service use case.
2. Domain-significant state changes should publish Bloom events.
3. Multi-step behavior should be modeled as a Flower flow.
4. Retry/backoff should not grow as ad-hoc scheduler code when Flower can own
   the step progression.
5. If the process needs periodic waiting, keep the flow alive on the Flower
   worker and advance by `stepNo`, timeout, event, or stored state.
6. The final code should make the business process readable by looking at:
   - aggregate state
   - event record names
   - Flower flow type
   - Flower step ids
   - `stepNo` transitions when needed

7. Runtime recovery must be based on durable business state unless and until
   Flower-native persistence is introduced. See
   `docs/architecture/FLOW_RECOVERY_POLICY.md`.

For HTTP APIs, keep the REST boundary synchronous and explicit. A create
endpoint should persist the command/job, submit the Flower flow, then return a
resource id such as `jobId`. UI progress should be read through normal query
APIs, usually polling the resource detail endpoint. Do not expose Bloom as the
public client contract.

## Document Render Flow Rule

Document rendering is a long-running asynchronous workflow. The REST API must
not wait for the file to be fully rendered before returning.

Target shape:

```text
REST create request
-> persist document_jobs
-> submit DocumentRenderFlow
-> return job response immediately
-> Flower dispatches worker command
-> Bloom/Flower events advance ACK, completion, failure, retry, timeout
-> document_jobs records progress for polling
```

Worker route rule:

- `ARCHDOX_AGENT`: the only document execution worker type. Cloud dispatches a
  WebSocket command to the selected ArchDox Agent.
- Agent deployment mode decides where that worker runs:
  - `LOCAL_OFFICE`: office PC/NAS/local storage execution.
  - `CLOUD_MANAGED`: managed cloud agent execution for personal users or offices
    that do not operate a local agent.
- Cloud API must not execute document-engine inline as a fallback.

The same `document_jobs` and `document_artifacts` tables remain the source of
truth for both routes. Route-specific ACK/completion events should update the
same progress fields so the web UI can poll one API contract.

Implemented Phase 4-3 shape:

```text
workerType=ARCHDOX_AGENT
deploymentMode=LOCAL_OFFICE or CLOUD_MANAGED
-> validate-job
-> render-archdox-agent-document
   stepNo 0: dispatch GENERATE_DOCUMENT command
   stepNo 10: wait ACK event
   stepNo 20: wait completion/failure event
   stepNo 30: Flower-owned backoff before redispatch
```

The ArchDox Agent command service publishes document render ACK/completion/failure
events after WebSocket messages arrive. The waiting Flower step subscribes to
those events and mutates `document_jobs` only from `onTick`, keeping Bloom as
event delivery and Flower as orchestration.

Before implementing a new document worker route, keep the existing progress
integration test green. That test verifies request/response behavior,
migration/persistence, background flow completion, and artifact metadata
creation for the current cloud route.

## Photo Pickup Flow

`PHOTO_PICKUP` retry/backoff and timeout handling is owned by the
`photo-pickup` Flower flow. The Cloud API command service creates command rows,
sends WebSocket commands, and publishes command ACK/completion/failure events
only. It must not decide semantic retry/backoff for photo pickup.

Implemented flow:

```text
flowType: photo-pickup
flowKey: photo:{photoId}

steps:
1. archdox-agent-photo-pickup

stepNo 0  = dispatch PHOTO_PICKUP command
stepNo 10 = wait ACK event
stepNo 20 = wait completion/failure event
stepNo 30 = Flower-owned retry backoff before redispatch
```

Implemented events:

- `PhotoUploadConfirmed`
- `PhotoPickupRequested`
- `PhotoPickupCommandAckedEvent`
- `PhotoPickupCommandCompletedEvent`
- `PhotoPickupCommandFailedEvent`
- `PhotoOriginalPickupCompleted`
- `PhotoOriginalPickupFailed`

Photo pickup retry settings are owned by `archdox.photos.pickup`:

- `max-attempts`
- `retry-base-delay-ms`
- `retry-max-delay-ms`
- `step-timeout-ms`
- `worker-interval-ms`

Each Flower retry creates a fresh `PHOTO_PICKUP` command and therefore a fresh
short-lived download URL. On final failure, the flow marks
`photos.original_pickup_status=FAILED` and publishes
`PhotoOriginalPickupFailed`.

Implemented derivative flow:

```text
flowType: photo-derivative-generation
flowKey: photo:{photoId}

steps:
1. prepare-source
2. generate-working
3. generate-thumbnail
4. finalize
```

Implemented derivative events:

- `PhotoUploadConfirmed`
- `PhotoDerivativesGenerated`
- `PhotoDerivativeGenerationFailed`

Derivative step state policy:

```text
prepare-source / generate-working / generate-thumbnail

stepNo 0  = submit background task
stepNo 10 = wait for task completion or step timeout
stepNo 20 = retry backoff wait
```

The derivative flow must not be a synchronous chain of service calls hidden
behind Flower step names. Each IO/heavy step submits work to the configured
photo derivative executor, returns `stay()`, then observes completion on a
later worker tick. If a task fails or times out, the same Step moves to
`stepNo=20` and waits for the configured backoff before retrying.

Derivative retry settings are owned by
`archdox.photos.derivatives`:

- `max-attempts`
- `retry-base-delay-ms`
- `retry-max-delay-ms`
- `step-timeout-ms`
- `worker-interval-ms`
- `executor-threads`

When the retry budget is exhausted, the flow publishes
`PhotoDerivativeGenerationFailed` with `stepId`, `attempt`, and `reason`, then
fails the Flower flow. `finalize` is intentionally immediate because it only
publishes `PhotoDerivativesGenerated` after the previous steps have completed.

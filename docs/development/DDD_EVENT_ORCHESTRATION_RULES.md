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
  - Worker: `photo-derivatives`
  - Steps: `prepare-source`, `generate-working`, `generate-thumbnail`,
    `finalize`
  - Retry cursor: stepNo `0` submit, `10` observe, `20` backoff
- `document-generation`
  - Trigger: REST entrypoint directly submits the flow after creating
    `document_jobs`
  - Flow input: `DocumentGenerationRequested`
  - Worker: `document-generation`
  - Steps: `validate-job`, `render-cloud-document`
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

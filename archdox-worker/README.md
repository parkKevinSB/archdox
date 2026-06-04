# archdox-worker

`archdox-worker` is the controlled ArchDox domain action layer.

It is not the same thing as a Flower runtime worker, and it is not the
`archdox-agent` process.

The module provides a small execution envelope around ArchDox actions:

```text
request
-> resolve registered action
-> load action definition
-> check policy/permission/state
-> execute through Flower
-> record trace/result
```

## Responsibilities

- Define `ArchDoxWorkerRequest`, `ArchDoxWorkerAction`, and result contracts.
- Provide action definitions, an action registry, and a policy gate boundary.
- Run allowed actions through `ArchDoxWorkerExecutionFlow`.
- Keep AI/user/system proposals from directly mutating domain state.
- Make worker actions traceable, testable, and eventually recoverable.

## Not Responsibilities

- Direct AI model calls.
- Prompt validation/retry/refine.
- Document rendering or file storage execution.
- WebSocket command transport to office/cloud runtimes.
- Replacing the existing ArchDox UI or domain services.

## Relationship To Other Modules

| Module | Relationship |
| --- | --- |
| `flower` | Provides the low-level flow/step/worker engine used to execute the action envelope. |
| `flower-ai-harness` | Provides a generic reliable AI task lifecycle. |
| `archdox-ai-harness` | Provides ArchDox-specific AI blocks that a worker action or higher-level worker task may call. |
| `archdox-agent` | Executes document/photo/artifact commands after Cloud API routes them. Worker actions may request document generation, but the Agent renders the file. |
| `cloud-api` | Wires real ArchDox action executors, permissions, DB state, REST endpoints, and UI integration. |

## Current Scope

Current V1 scope is intentionally small:

- Execute one registered action at a time.
- Use existing ArchDox domain services as the source-of-truth mutation path.
- Support Worker Chat as an alternate UI entry point into the same project,
  site, report, preflight, and document generation flow.

Longer multi-step worker tasks should become explicit Flower flows instead of
being hidden inside one large action executor.

## Action Definition Boundary

`ArchDoxWorkerActionDefinition` describes the controlled action before policy
or execution:

```text
actionType
owner
executorName
enabled
readOnly
riskLevel
requiresApprovalByDefault
supportsDryRun
allowedSources
requiredContextFields
description
```

The registry may define future actions while keeping them disabled or without a
registered executor. This lets ArchDox document the intended worker boundary
without accidentally opening production actions. Cloud API policy gates should
read this definition instead of keeping a separate hardcoded enabled-action
list.

## Controlled Agent Direction

`archdox-worker` is the ArchDox host-application validation ground for the
`flower-agent-runtime` idea.

The intended rule is:

```text
planner/user/system proposes an ArchDox action
-> action registry resolves only known actions
-> policy gate checks source, context, permissions, state, approval, and budget
-> Flower executes the action envelope
-> the domain service performs the actual mutation
-> operation events record the trace
```

This is deliberately different from handing arbitrary tools to an AI model.
The AI planner's output is data, not authority. ArchDox decides whether a
proposal is executable.

## Governance Metrics Boundary

`archdox-worker` must remain measurable, but it should not create an
unbounded metrics/logging subsystem by itself.

The host application should record durable Worker trace events through its
normal operation/audit event mechanism, then aggregate those events for admin
views:

```text
request received
policy allowed
policy denied
approval required
action succeeded
action failed
action rejected
action unknown
```

The first useful measurements are:

```text
catch rate
approval-required rate
failure rate
event distribution
reason-code distribution
action/event distribution
bounded recent trace samples
```

These are governance signals, not business truth tables. They should be kept
short-window and bounded unless a later approval/replay feature introduces a
real need for longer retention. Metrics such as false-positive rate, override
rate, approval latency, audit reconstruction, and replay success should be
added only after ArchDox has explicit human feedback, approval, and replay
records to calculate them honestly.

## Approval Replay Rule

Approval is represented by the host application, not by `archdox-worker`
mutating state directly.

The generic worker envelope only emits `PENDING_APPROVAL` /
`APPROVAL_REQUIRED`. Cloud API may persist that as an approval request and, once
approved, submit a new Worker execution with approval metadata in the action
payload.

The policy gate must verify the approval against durable host state before
allowing the replay. A payload field alone is never authority.

## Document Generation Boundary

Document generation has two separate layers:

```text
controlled action boundary
  REQUEST_DOCUMENT_GENERATION
  - validates source/context/policy through archdox-worker when invoked by a worker
  - calls Cloud API's document generation request boundary

recipe/orchestration flow
  DocumentGenerationWorker / DocumentGenerationFlow
  - validates the durable DocumentJob
  - routes to ArchDox Agent
  - waits for ACK/COMPLETED/FAILED events
  - updates job progress and artifacts
```

The request boundary is now centralized in Cloud API's
`DocumentGenerationRequestService`, so normal REST requests and worker-chat
requests create a `DocumentJob` and submit the same Flower document generation
flow in the same way.

If document generation later needs AI planning, legal review, stronger-model
reruns, approval gates, or submission-package preparation, that should be a
higher-level worker task flow that coordinates registered actions. It should
not hide those steps inside the low-level render flow or the Agent.

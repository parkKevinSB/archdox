# archdox-worker

`archdox-worker` is the controlled ArchDox domain action layer.

It is not the same thing as a Flower runtime worker, and it is not the
`archdox-agent` process.

The module provides a small execution envelope around ArchDox actions:

```text
request
-> resolve registered action
-> check policy/permission/state
-> execute through Flower
-> record trace/result
```

## Responsibilities

- Define `ArchDoxWorkerRequest`, `ArchDoxWorkerAction`, and result contracts.
- Provide an action registry and policy gate boundary.
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

# Flow Recovery Policy

ArchDox uses Flower as the runtime orchestration engine. Current Flower runtime
state is in memory. The durable source of truth is the ArchDox database:

- `photos`
- `photo_assets`
- `document_jobs`
- `document_delivery_requests`
- `archdox_agent_commands`
- `archdox_agent_sessions`

This means Cloud API restart recovery is based on durable business state, not on
serialized Flower internals.

## Current Recovery Model

On Cloud API startup, `ArchDoxFlowRecoveryService` performs one recovery pass:

1. Acquire a Postgres advisory lock so only one Cloud API instance performs the
   startup recovery pass at a time.
2. Expire non-terminal Agent commands that were in-flight when the previous
   runtime stopped:
   - `PENDING`
   - `DELIVERED`
   - `ACKED`
3. Re-submit missing Flower flows from DB state:
   - `photos.status=UPLOADED`,
     `photos.original_pickup_status=PENDING`,
     `photos.upload_target=CLOUD_MEDIATED`
     -> `photo-pickup`
   - `document_jobs.status in (REQUESTED, GENERATING)`
     -> `document-generation`
   - `document_delivery_requests.status=SENDING`
     -> `document-delivery`

The recovered flows create fresh Agent commands where needed. This prevents old
WebSocket commands from being delivered after the runtime that owned their
Flower step has disappeared.

After the pass completes, Cloud records a `FLOW_RECOVERY_COMPLETED` operation
event with the recovered counts. This is an operational breadcrumb only; the
domain tables remain the source of truth for what should run.

## Why This Does Not Require Flower Persistence Yet

Flower persistence would store flow runtime details such as current step,
stepNo, signal state, and timeout state. That is useful later, but it is not
required for MVP recovery because ArchDox workflows already persist their
business state in domain tables.

Current rule:

```text
DB business state = source of truth
Flower runtime state = recoverable orchestration state
```

When a flow is re-submitted, it restarts from its first step and checks durable
state before doing work. Steps must therefore be idempotent enough to tolerate
restart.

## Agent Command Recovery Rule

Agent commands are transport records, not the business source of truth. After a
Cloud API restart, non-terminal commands are expired with a runtime recovery
message. Recovered flows create new commands with fresh payloads and fresh
short-lived URLs.

If an Agent reports ACK/COMPLETE/FAIL for an expired command, Cloud ignores the
terminal command update. The active recovered flow waits for the new command.

## Known Limit

The current recovery pass protects startup recovery with a Postgres advisory
lock, but it is still a recovery foundation, not full distributed Flower
persistence. A later phase should add one of these:

- Flower-native persistence for flow state
- ArchDox workflow instance registry with durable ownership/lease
- durable queue/outbox for cross-instance command dispatch

Until then, workflow steps must remain idempotent and DB state must remain the
business source of truth.

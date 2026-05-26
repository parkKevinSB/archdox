# Cloud API Scaling And Routing

This document defines how ArchDox behaves when multiple Cloud API instances run
behind a load balancer.

## Front Door

Production should have a front door in front of API instances.

AWS-oriented target:

```text
app.archdox.com
-> CloudFront + S3
-> React static files

api.archdox.com
-> ALB
-> Cloud API instances
-> RDS PostgreSQL / Redis or Queue
```

Nginx-oriented early deployment:

```text
archdox.example.com
-> Nginx
   /          -> React static files
   /api/*     -> Cloud API upstream pool
   /agent/ws  -> Cloud API WebSocket upstream pool
```

Cloud API should not be the required owner of the React static files. Serving
React from CloudFront/S3 or Nginx keeps API scaling independent from frontend
asset delivery.

## Stateless User API

User-facing REST APIs must be stateless at the API instance level.

```text
Browser
-> Load Balancer
-> any Cloud API instance
-> JWT verification
-> office membership check
-> PostgreSQL query/update
-> response
```

Rules:

- Do not store login state in one API instance's memory.
- Access token is a JWT and can be verified by any API instance.
- Refresh token state is stored in DB as a hash.
- Active office is supplied per request with `X-Office-Id`.
- Document lists, project lists, report state, job progress, and delivery state
  are read from DB, not from API memory.
- Sticky sessions are not required for normal REST APIs.

## API Instances Must Not Directly Call Each Other

Cloud API instances should not coordinate through direct HTTP calls.

Avoid:

```text
API #2 -> API #1 -> Agent WebSocket
```

Use shared coordination instead:

```text
API #2
-> DB command insert
-> Redis Pub/Sub or Postgres NOTIFY wakeup

API #1
-> receives wakeup
-> checks its in-memory WebSocket sessions
-> pushes to the connected Agent
```

The source of truth must be DB/queue state, not instance memory.

## Agent WebSocket Difference

Agent WebSocket is different from normal REST because the physical connection is
attached to one API instance.

Example:

```text
Office 1 ArchDox Agent -> API #1 WebSocket
Office 2 ArchDox Agent -> API #2 WebSocket

Office 1 user request -> API #2
```

This is valid. API #2 may handle the user request, but API #2 must not try to
read Office 1 NAS or call API #1 directly. It should create DB state and command
state. The instance that has the Agent WebSocket delivers the command.

## Command Routing

Command delivery must be DB-backed.

```text
User/API command request
-> any Cloud API instance
-> validate tenant and job state
-> select target archdox_agents row, preferring ACTIVE archdox_agent_sessions
-> insert archdox_agent_commands row
-> current MVP: due-command scanner runs on API instances
-> later optimization: wake connected API instances
-> matching API instance pushes WebSocket command
-> Agent ACK/COMPLETE/FAIL
-> Cloud updates command/job/delivery state
```

In-memory session registry is only a fast transport cache. The command record is
the durable truth.

## Agent Sessions

The schema includes `archdox_agent_sessions`:

```sql
archdox_agent_sessions (
  id BIGSERIAL PRIMARY KEY,
  office_id BIGINT NOT NULL REFERENCES offices(id),
  agent_id BIGINT NOT NULL REFERENCES archdox_agents(id),
  api_instance_id TEXT NOT NULL,
  websocket_session_id TEXT NOT NULL,
  status TEXT NOT NULL,
  connected_at TIMESTAMPTZ NOT NULL,
  last_seen_at TIMESTAMPTZ NOT NULL,
  disconnected_at TIMESTAMPTZ,
  disconnect_reason TEXT,
  UNIQUE (api_instance_id, websocket_session_id)
)
```

Expected indexes:

```sql
CREATE INDEX ix_archdox_agent_sessions_agent_status
  ON archdox_agent_sessions (agent_id, status, last_seen_at DESC);

CREATE INDEX ix_archdox_agent_sessions_instance_status
  ON archdox_agent_sessions (api_instance_id, status);

CREATE INDEX ix_archdox_agent_sessions_office_status
  ON archdox_agent_sessions (office_id, status, last_seen_at DESC);
```

Session rows are operational state. They help observability and routing, but a
stale session must not lose commands because pending commands remain in
`archdox_agent_commands`.

`api_instance_id` must be unique per running Cloud API process. On Cloud API
startup, ACTIVE sessions for the same `api_instance_id` are marked
DISCONNECTED. This protects stable instance IDs from restart leftovers.

Heartbeat timeout cleanup is an always-on Flower flow, not a Spring scheduler.
The `monitoring` worker runs `agent-connection-health-monitor` as a long-lived
flow:

```text
stepNo 0 CHECK
-> find ACTIVE archdox_agent_sessions where last_seen_at is older than timeout
-> ask the in-memory session registry to close the local socket if this API owns it
-> mark the session DISCONNECTED
-> mark the Agent OFFLINE only when no ACTIVE session remains
-> record AGENT_HEARTBEAT_TIMEOUT operation event
-> stepNo 100 WAIT
-> timeout
-> stepNo 0 CHECK
```

The Flower flow reads durable session state and delegates socket cleanup to the
registry. It must not keep WebSocket objects inside the flow or step state.

When an Agent opens a new WebSocket session, ACTIVE sessions for the same
logical `agent_id` are not automatically replaced. Cloud API must first clean
up heartbeat-timed-out sessions for that Agent. If an ACTIVE session still
exists after that cleanup, the new HELLO is rejected with an Agent channel
error and the new socket is closed.

This prevents a healthy Agent that may be rendering a document from being
silently displaced by another process using the same `AGENT_ID` and
`AGENT_DEVICE_SECRET`. One registered Agent should have one current transport
session; additional runtime capacity should be registered as separate Agent
codes.

If the old session is confirmed disconnected, any in-flight commands owned by
that Agent are failed. For document rendering this publishes a
`DocumentRenderCommandFailedEvent` with `ARCHDOX_AGENT_DISCONNECTED` and
`retryable=false`, so the document generation flow fails the current
`document_job`. The user can request a fresh document generation after the
Agent reconnects.

During command dispatch, if the current API instance has no open in-memory
WebSocket for the selected Agent, it must not pretend the command was delivered.
The command remains pending, and ACTIVE sessions for that same
`agent_id + api_instance_id` are marked DISCONNECTED as stale. Sessions owned by
other API instances are not force-disconnected by this local check.

## Agent Selection

When creating a command, Cloud API should select an Agent by:

1. `office_id`
2. `deployment_mode`
3. ACTIVE `archdox_agent_sessions` ordered by `last_seen_at`
4. `status = ONLINE`
5. required capability
6. primary/preferred flag when implemented
7. latest `last_seen_at`

MVP can operate with one primary `LOCAL_OFFICE` Agent per office while allowing
multiple registered Agents in the schema.

Cloud-managed Agents can be treated as a worker pool later. They should still be
selected through the same Agent selection abstraction.

## File Delivery Rule

Cloud API instances must not directly access office NAS.

For metadata:

```text
any API instance
-> DB query
-> response
```

For NAS-backed files:

```text
any API instance
-> document_delivery_request
-> archdox_agent_commands
-> Agent reads NAS/configured storage
-> Agent uploads/prepares temporary download target
-> DB delivery state updated
-> UI polls any API instance
-> browser downloads from prepared URL
```

Direct Cloud streaming is allowed only for Cloud-owned storage such as
`API_LOCAL` or S3-compatible artifacts.

## Recommended Phases

1. MVP single API instance: in-memory WebSocket session cache plus DB commands.
2. Multi-instance foundation: `archdox_agent_sessions`, `api_instance_id`, and
   DB-backed session state.
3. Wakeup optimization: Redis Pub/Sub or Postgres LISTEN/NOTIFY.
4. Dedicated Agent Gateway only if WebSocket scale or operations require it.

Do not introduce a dedicated Agent Gateway before the DB-backed command/session
model is proven.

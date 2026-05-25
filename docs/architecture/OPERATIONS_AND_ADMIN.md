# Operations And Admin Roadmap

This document records the operations, admin, monitoring, logging, and future
Ops Agent requirements for ArchDox. Some items are future work, but the
architecture must leave room for them.

Phase 4-8 establishes the first database-backed operation event log. It is not
full observability, full audit logging, or a metrics stack yet. It is the
minimum durable event trail for workflow and operational facts.

## Goal

ArchDox must be operable after it becomes more than a single developer machine.
The system should let an operator understand:

- who uses the service
- which offices are active
- which Cloud API instances are healthy
- which ArchDox Agents are connected
- whether document generation, photo pickup, and delivery flows are stuck
- whether data is accumulating in an abnormal state
- which errors need human attention

The first implementation should be simple and database-backed. Prometheus,
Grafana, Loki, OpenSearch, or a dedicated observability stack can be added after
the operational model is clear.

## Admin Console Scope

The `admin` module is the human operations console.

It should eventually provide:

- user search and user detail
- office search and office detail
- office membership management
- plan and usage view
- Cloud API instance health view
- ArchDox Agent connection/session view
- ArchDox Agent command queue view
- document job monitoring
- photo upload and pickup monitoring
- delivery request monitoring
- operational event and audit log search
- manual retry, cancel, or repair actions for selected flows

Admin UI must not bypass Cloud API authorization. It should call admin REST APIs
with explicit platform/admin roles.

## Implemented Admin UI Foundation

Phase 5-2 adds the first React-based office operations console in the `admin`
module.

Implemented structure:

- Vite + React + TypeScript
- `admin/src/api.ts`: Cloud API client
- `admin/src/types.ts`: API response types used by the console
- `admin/src/App.tsx`: login, office selection, navigation, and views
- `admin/src/styles.css`: operations-console styling

Implemented views:

- Dashboard: summary metrics, workflow status bars, recent operation events
- Agents: registered ArchDox Agents and recent WebSocket sessions
- Commands: Agent command transport records
- Document Jobs: document generation state and artifact metadata
- Members: office member list, add/reactivate, role change, and deactivate
- Templates: office template definitions, template revisions, DOCX upload,
  publication, and office template override assignment
- Photos: photo upload, pickup, and asset state
- Deliveries: document delivery request state
- Events: operation event timeline

Current behavior:

- authenticates through `/api/v1/auth/login`
- loads user/offices through `/api/v1/me`
- uses `X-Office-Id` for the selected office
- stores access/refresh tokens in browser local storage for MVP only
- calls the Phase 5-1 office-scoped ops read APIs
- calls the configuration registry APIs for office-owned template management

Rules:

- this UI is for office `OWNER` and `ADMIN` users first
- workflow operations remain read-only until explicit repair/retry/cancel flows
  are designed
- office membership mutations must use the tenant-scoped office member APIs and
  preserve owner-protection rules
- template/configuration mutations are allowed only through the configuration
  registry APIs and must preserve versioning rules
- it must not show command payloads, signed URLs, install tokens, device secret
  hashes, raw file contents, or raw GPS coordinates
- platform-wide support/admin console remains future work

Template management behavior:

- creates office-owned document template definitions
- creates draft document template revisions
- uploads DOCX files to draft revisions
- publishes draft revisions after content upload
- assigns a published template revision as the office override for a report type
- does not edit published revision content in place; changes require a new
  revision

Local development:

```bash
cd admin
npm install
npm run dev
```

The Vite dev server proxies `/api` to `http://localhost:8080`.

## Implemented Office Ops Read API

Phase 5-1 adds office-scoped read APIs for an office admin console.

Implemented endpoints:

- `GET /api/v1/office-ops/summary`
- `GET /api/v1/office-ops/agents`
- `GET /api/v1/office-ops/agent-sessions`
- `GET /api/v1/office-ops/agent-commands`
- `GET /api/v1/office-ops/document-jobs`
- `GET /api/v1/office-ops/photos`
- `GET /api/v1/office-ops/document-deliveries`

Rules:

- caller must be an active member of the office selected by `X-Office-Id`
- caller must have office role `OWNER` or `ADMIN`
- APIs are read-only and must not mutate workflow state
- APIs must not expose secrets, install tokens, device secret hashes, signed
  URLs, raw file contents, or full Agent command payloads
- this is not cross-office platform admin access
- platform admin APIs remain a separate future layer

## User And Office Administration

Required capabilities:

- list users
- view a user profile, status, joined offices, and recent activity
- suspend or reactivate a user
- list offices
- view office plan, members, usage, Agents, jobs, photos, artifacts
- invite, remove, or change office members
- transfer office ownership
- view security-relevant activity such as login, install token issue, Agent
  pairing, failed auth attempts, and destructive actions

Rules:

- normal office membership APIs are tenant-scoped user features
- platform admin APIs are cross-tenant and must require a stronger role
- admin mutations must create audit events
- avoid hard deletes for users, offices, memberships, jobs, and artifacts unless
  a retention policy explicitly allows it

## Implemented Office Member Management API

Phase 5-3 adds tenant-scoped office member management APIs.

Implemented endpoints:

- `GET /api/v1/offices/{officeId}/members`
- `POST /api/v1/offices/{officeId}/members`
- `PATCH /api/v1/offices/{officeId}/members/{memberUserId}/role`
- `DELETE /api/v1/offices/{officeId}/members/{memberUserId}`

Implemented behavior:

- office `OWNER` and `ADMIN` can list active/suspended office memberships
- office `OWNER` and `ADMIN` can add an already signed-up user by email
- adding a previously suspended/left member reactivates that membership
- office `OWNER` and `ADMIN` can change non-owner member roles
- `DELETE` suspends the membership instead of hard-deleting it
- member mutations create operation events

Protection rules:

- only `OWNER` can assign `OWNER` role
- only `OWNER` can modify another `OWNER`
- an actor cannot change or deactivate their own membership
- an office must always keep at least one active `OWNER`

Current limitation:

- true invite-by-email is not implemented yet; the target user must already
  have signed up

## Implemented Office Member Admin UI

Phase 5-4 connects the Admin React console to the Phase 5-3 office member
management APIs.

Implemented UI behavior:

- adds a `Members` tab to the operations console
- loads members from `GET /api/v1/offices/{officeId}/members`
- adds or reactivates an already signed-up user by email
- changes a member role through the role update API
- deactivates a member through the membership deactivate API
- creates and cancels office invitation tokens
- shows a one-time invite URL after invitation creation
- supports invite acceptance through the admin React app path
  `/office-invitations/{token}` after signup/login
- shows active member count, active owner count, and the current operator role
- disables self role-change and self-deactivation controls in the UI

Rules:

- the UI is a convenience layer; Cloud API remains the authority for all
  membership authorization rules
- only office `OWNER` users can select or assign the `OWNER` role
- deactivation means membership suspension, not hard deletion
- the UI must not implement direct database-like user creation
- email delivery remains a later feature; the first invite flow uses copied
  invite URLs

## Implemented Office Invitation Foundation

Phase 5-5 adds tenant-scoped office invitations.

Implemented endpoints:

- `GET /api/v1/offices/{officeId}/invitations`
- `POST /api/v1/offices/{officeId}/invitations`
- `DELETE /api/v1/offices/{officeId}/invitations/{invitationId}`
- `POST /api/v1/office-invitations/{token}/accept`

Implemented behavior:

- office `OWNER` and `ADMIN` can create/list/cancel invitations
- only office `OWNER` can create an `OWNER` invitation
- invitations store token hashes, not raw tokens
- raw accept token is returned only once, when the invitation is created
- invitee must sign up or log in before accepting
- invitee email must match the invitation email
- accepting an invitation creates or reactivates the office membership
- cancelled, accepted, and expired invitations cannot be reused
- invitation actions create operation events

Current limitation:

- no SMTP/SES/SendGrid email delivery yet
- no polished invite landing page outside the admin React app yet
- if the one-time invite URL is lost, the office admin should cancel or let the
  invitation expire and create a new one

## Platform Admin Roles

Phase 9-4 adds the platform-level admin model. This is separate from office
membership.

Implemented concepts:

- `platform_admins`
- roles: `SUPER_ADMIN`, `SUPPORT`, `READONLY_SUPPORT`, `BILLING`
- status: `ACTIVE`, `DISABLED`
- `GET /api/v1/platform-admin/me`
- optional bootstrap through `PLATFORM_ADMIN_BOOTSTRAP_EMAILS`
- optional IP allowlist or MFA requirement later

Office roles such as `OWNER`, `ADMIN`, `MEMBER`, and `VIEWER` must not grant
cross-tenant admin access.

## Implemented Platform Ops API And UI

Phase 9-5 and Phase 9-6 add a first platform operations layer.

Implemented platform APIs:

- `GET /api/v1/platform-admin/ops/summary`
- `GET /api/v1/platform-admin/ops/users`
- `GET /api/v1/platform-admin/ops/offices`
- `GET /api/v1/platform-admin/ops/agents`
- `GET /api/v1/platform-admin/ops/agent-commands`
- `GET /api/v1/platform-admin/ops/document-jobs`
- `GET /api/v1/platform-admin/ops/photos`
- `GET /api/v1/platform-admin/ops/deliveries`
- `GET /api/v1/platform-admin/ops/events`
- `POST /api/v1/platform-admin/ops/health/detect-stuck`

Implemented UI:

- the existing admin React app now exposes a separate `Platform` section only
  when `/api/v1/platform-admin/me` succeeds
- office admin views still require `X-Office-Id` and office `OWNER`/`ADMIN`
- platform views do not require selecting an office and can see cross-office
  operational data

Rules:

- office admins must never gain platform access through office role alone
- platform APIs must not expose secrets, device secret hashes, install tokens,
  signed URLs, or raw file content
- platform views are for support/operations, not normal office workflows

## Operational Event Model

ArchDox stores important operational facts in a compact table instead of
putting all raw logs into the database.

Implemented table:

```sql
operation_events (
  id BIGSERIAL PRIMARY KEY,
  office_id BIGINT REFERENCES offices(id),
  severity TEXT NOT NULL,
  event_type TEXT NOT NULL,
  workflow_type TEXT,
  workflow_key TEXT,
  resource_type TEXT,
  resource_id TEXT,
  actor_user_id BIGINT REFERENCES users(id),
  correlation_id TEXT,
  message TEXT NOT NULL,
  payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL
)
```

Implemented event types:

- `FLOW_RECOVERY_COMPLETED`
- `AGENT_COMMAND_ENQUEUED`
- `AGENT_COMMAND_EXPIRED_FOR_RECOVERY`
- `AGENT_COMMAND_ACKED`
- `AGENT_COMMAND_COMPLETED`
- `AGENT_COMMAND_FAILED`
- `PHOTO_PICKUP_COMPLETED`
- `PHOTO_PICKUP_FAILED`
- `DOCUMENT_JOB_REQUESTED`
- `DOCUMENT_JOB_GENERATED`
- `DOCUMENT_JOB_FAILED`
- `DOCUMENT_DELIVERY_REQUESTED`
- `DOCUMENT_DELIVERY_COMPLETED`
- `DOCUMENT_DELIVERY_FAILED`

Future event types:

- `AGENT_CONNECTED`
- `AGENT_DISCONNECTED`
- `INSTALL_TOKEN_ISSUED`
- `USER_SUSPENDED`
- `DB_INVARIANT_VIOLATION`

Phase 9-7 stuck detection event types:

- `AGENT_COMMAND_STUCK_DETECTED`
- `DOCUMENT_JOB_STUCK_DETECTED`
- `PHOTO_PICKUP_STUCK_DETECTED`
- `DOCUMENT_DELIVERY_STUCK_DETECTED`

Office member events:

- `OFFICE_MEMBER_ADDED`
- `OFFICE_MEMBER_REACTIVATED`
- `OFFICE_MEMBER_ROLE_CHANGED`
- `OFFICE_MEMBER_DEACTIVATED`

Office invitation events:

- `OFFICE_INVITATION_CREATED`
- `OFFICE_INVITATION_CANCELLED`
- `OFFICE_INVITATION_ACCEPTED`
- `OFFICE_INVITATION_EXPIRED`

Rules:

- raw application logs are not the same as operation events
- operation events are searchable through office-scoped API first
- cross-office/platform admin search is a later admin API, not the MVP endpoint
- only important, structured facts should be stored here
- use correlation IDs so API logs, Agent commands, jobs, and operation events
  can be connected
- do not store secrets, signed URLs, install tokens, device secrets, passwords,
  or raw file contents in `payload_json`
- `workflow_type` and `workflow_key` connect an event to an async flow such as
  `photo-pickup`, `document-generation`, or `document-delivery`
- `resource_type` and `resource_id` identify the concrete business object such
  as `PHOTO`, `DOCUMENT_JOB`, `DOCUMENT_DELIVERY_REQUEST`, or `AGENT_COMMAND`
- `office_id = null` is allowed only for platform/system events such as startup
  recovery summary; normal office workflows must include `office_id`

Current API:

- `GET /api/v1/operation-events`
- requires authenticated office context through `X-Office-Id`
- supports filters: `eventType`, `workflowType`, `workflowKey`,
  `resourceType`, `resourceId`, `limit`
- default limit is `50`
- max limit is `200`

## Cloud API Instance Monitoring

Multiple Cloud API instances can run behind a load balancer. Each instance
should publish health and heartbeat information.

Suggested table:

```sql
cloud_api_instances (
  id TEXT PRIMARY KEY,
  version TEXT,
  status TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  last_seen_at TIMESTAMPTZ NOT NULL,
  hostname TEXT,
  metadata_json JSONB
)
```

Implementation path:

1. generate or configure `archdox.agent.api-instance-id`
2. write/update `cloud_api_instances` on startup and scheduled heartbeat
3. expose Admin API for current instances
4. later export metrics through Spring Actuator/Micrometer

Rules:

- REST APIs must stay stateless even when instance rows exist
- instance rows are operational visibility, not business ownership
- instance-to-instance direct calls are still prohibited

## ArchDox Agent Monitoring

Admin should show:

- registered Agents per office
- deployment mode: `LOCAL_OFFICE` or `CLOUD_MANAGED`
- auth mode and last authenticated time
- current status
- current `archdox_agent_sessions`
- latest heartbeat
- capabilities and storage profile
- pending/delivered/acked/failed commands
- recent disconnect reasons

Important stuck states:

- Agent row is ONLINE but no ACTIVE session exists for too long
- ACTIVE session has stale `last_seen_at`
- `PHOTO_PICKUP` command is PENDING/DELIVERED beyond retry expectation
- `GENERATE_DOCUMENT` command is ACKED but job progress is not moving
- repeated failures from the same Agent or office

## Document Job Monitoring

Admin should show:

- requested jobs
- current status and progress step
- worker type: `CLOUD` or `ARCHDOX_AGENT`
- selected Agent/command if any
- retry count and last failure
- artifacts generated
- delivery requests

Automated checks should detect:

- `REQUESTED` jobs not started within a threshold
- `GENERATING` jobs stuck at the same progress step
- `WAITING_FOR_AGENT` jobs without a live Agent session
- `GENERATED` jobs without expected artifacts
- failed jobs grouped by template, office, or Agent version

## Photo Pipeline Monitoring

Admin should show:

- upload intents and confirmed uploads
- `ORIGINAL`, `WORKING`, and `THUMBNAIL` asset status
- temporary original age
- pickup status
- derivative generation status
- object storage key and storage kind

Automated checks should detect:

- uploaded original without derivative assets
- office-plan original not picked up by ArchDox Agent
- temporary cloud original not deleted after successful pickup
- `AGENT_MANAGED` asset with missing pickup completion event
- repeated derivative generation failures

## Logging Strategy

Use layered logging:

1. application logs for debugging
2. operation events for important searchable facts
3. metrics for counters, latency, and gauges
4. audit logs for security-sensitive actions

Initial implementation:

- Logback rolling file appenders for Cloud API and ArchDox Agent
- 30-day default rolling history
- `X-Correlation-Id` request/response header
- MDC fields: `correlationId`, `httpMethod`, `httpPath`
- log important flow IDs: `officeId`, `reportId`, `documentJobId`,
  `photoId`, `agentId`, `commandId`
- keep raw logs outside the business database

Do not store secrets, tokens, device secrets, signed URLs, passwords, or raw
file contents in logs.

## Metrics And Prometheus

Prometheus is useful, but it is not required for the first admin implementation.
The low-friction path is:

1. keep Spring Boot Actuator enabled
2. expose health and metrics only on protected internal/admin networks
3. add Micrometer counters/gauges for key flows
4. add Prometheus scraping when deployment has stable infrastructure
5. add Grafana dashboards later

Suggested metrics:

- HTTP request count, latency, and error count
- DB connection pool usage
- document job status count
- document generation duration
- photo pickup pending count
- photo pickup failure count
- ArchDox Agent active session count
- ArchDox Agent command status count
- delivery request failure count

Metrics are for aggregate trends. Admin DB views are for exact business and
operational state.

## Ops Agent Concept

The future personal/owner-operated monitoring process should be a separate
runtime from ArchDox Agent.

Suggested name:

- `ArchDox Ops Agent`

It should not be confused with `ArchDox Agent`, which executes document/photo
work.

Ops Agent responsibilities:

- read protected Admin/Ops APIs
- download archived application logs from object storage
- verify collection with checksum or manifest
- summarize abnormal events
- detect stuck DB states
- produce human-readable reports for the owner
- optionally open maintenance tickets or notifications later

Ops Agent should initially be read-only. Destructive actions such as deleting
logs, cancelling jobs, or repairing data should require explicit approval or a
separate high-privilege token.

## Log Collection And Retention

Do not make the first Ops Agent delete live logs directly.

Safer flow:

```text
Cloud API / infrastructure
-> writes logs to file, CloudWatch, Loki, or object storage
-> rolls logs into archived objects
-> Ops Agent downloads archived objects
-> Ops Agent records checksum and collected_at
-> retention lifecycle deletes old archives after the configured period
```

Rules:

- keep enough logs for incident investigation
- deletion should be lifecycle-policy based when possible
- Ops Agent may request cleanup, but should not be the only retention control
- operational summaries can remain longer than raw logs

## DB Health Checks

Ops checks should look for abnormal accumulated data.

Examples:

- stuck `document_jobs`
- `document_artifacts` missing for generated jobs
- `archdox_agent_commands` stuck in `PENDING`, `DELIVERED`, or `ACKED`
- ACTIVE `archdox_agent_sessions` with stale `last_seen_at`
- temporary photo originals older than policy
- failed photo derivative generation without retry
- delivery requests stuck in `SENDING`
- orphaned child rows
- cross-office reference violations
- unusually high error count for one office, Agent, template, or API version

Phase 9-7 implements the first on-demand detector:

```text
POST /api/v1/platform-admin/ops/health/detect-stuck
```

It records operation events for old in-flight document jobs, Agent commands,
photo pickups, and document deliveries. It is intentionally on-demand first.
Do not add an always-on scheduler for this detector without explicit approval.

## Audit Logging

Audit logs are required for security and support.

Audit-worthy actions:

- login success/failure
- password reset
- user suspension/reactivation
- office creation/update
- member invite/remove/role change
- install token issue/use/revoke
- Agent pairing/auth failure
- document delete/cancel/retry
- artifact delivery request
- admin impersonation or support access, if implemented

Audit logs should be append-only and should not contain secrets.

## Suggested Implementation Phases

### Ops Phase 1: Admin Read Models

- office-scoped ops read APIs are implemented for Phase 5-1
- platform admin role foundation is implemented for Phase 9-4
- platform Admin read APIs for users, offices, jobs, photos, Agents, commands,
  deliveries, and operation events are implemented for Phase 9-5
- admin UI has an office section and a platform section as of Phase 9-6

### Ops Phase 2: Operation Events And Audit Logs

- `operation_events` foundation is implemented
- request correlation id and rolling file logs are implemented for Phase 9-3
- audit log table
- more important events from Agent connect/disconnect and auth flows
- Admin search UI

### Ops Phase 3: Instance And Health Dashboard

- `cloud_api_instances`
- API instance heartbeat
- protected Actuator endpoints
- on-demand stuck detection is implemented for Phase 9-7
- always-on Agent/session stale detection remains future work until scheduler or
  queue-based monitoring is explicitly approved

### Ops Phase 4: Metrics Stack

- Micrometer custom metrics
- Prometheus scrape config
- Grafana dashboards
- alert rules for stuck jobs and high error rate

### Ops Phase 5: ArchDox Ops Agent

- read-only admin token
- log archive manifest API
- archived log download
- DB invariant report
- owner summary report
- optional notification channel

## Non-Goals For Early MVP

- full Prometheus/Grafana before basic Admin read models
- direct DB writes by Ops Agent
- direct deletion of live logs by Ops Agent
- using raw logs as the only source of operational truth
- bypassing Cloud API authorization from the admin frontend

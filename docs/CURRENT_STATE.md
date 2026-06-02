# ArchDox Current State

Last updated: 2026-06-03

This file is a short operational snapshot. It is not a replacement for the
architecture documents. Keep it current after major phase completions so AI
agents and humans can re-enter the project without reading every Markdown file.

## Platform Position

ArchDox is a document workflow orchestration platform for architecture office
workflows. Document generation is one important capability, but the system is
designed around the full lifecycle:

```text
project/site/target
-> report data and checklist input
-> photos and evidence
-> validation/review
-> template/output layout binding
-> ArchDox Agent rendering
-> artifact delivery and storage
-> operations and recovery
```

## Local Runtime

Current local development addresses:

| Component | Address |
| --- | --- |
| Client web | `http://127.0.0.1:5173` |
| Admin web | `http://127.0.0.1:5174` |
| Cloud API | `http://localhost:8080` |
| ArchDox Agent | `http://localhost:18080` |
| PostgreSQL | `localhost:55432` |
| MinIO API | `http://localhost:9000` |
| MinIO console | `http://localhost:9001` |
| MailHog SMTP/UI | `localhost:1025`, `http://localhost:8025` |

Local database convention:

```text
database: archdox
username: archdox
password: archdox
container: archdox-postgres-55432
```

Development accounts:

| Account | Password | Purpose |
| --- | --- | --- |
| `new-user@test.co.kr` | `password-1234` | normal user test account |
| `archdox-admin@test.co.kr` | `password-1234` | platform admin test account |

These are development-only credentials.

## Implemented Foundations

- Account, office, invitation, personal workspace, and role foundations.
- Project, site, inspection target, checklist schema, report, report revision,
  and permission foundations.
- Photo asset foundations, S3-compatible storage direction, working image and
  thumbnail policy, and local/office storage policy.
- ArchDox Agent registration, WebSocket control channel, command ACK/COMPLETE
  events, duplicate session policy, heartbeat/stale-session handling, and
  command routing foundation.
- Document job, artifact, delivery, status/progress, and recovery foundations.
- Agent-based document rendering with HTML, DOCX, and PDF export direction.
- Template/configuration registry foundations, template upload/storage, output
  layout, and neutral document data model direction.
- Office knowledge platform direction is documented: accumulated structured
  project/site/report/checklist/photo/finding/document/operation data should be
  treated as future office memory, not just temporary document-generation input.
- Site supervision ledger foundation exists: construction daily log `DAILY_LOG`
  saves now synchronize structured trade/process/item/photo observations into
  `site_supervision_entries`, and report submission promotes the matching
  report revision entries from `DRAFT` to `CONFIRMED`. Future trade-specific
  checklist and supervision-report screens should reuse this ledger rather than
  scraping another document's step payload.
- Preflight document review gate tied to report revision state.
- AI harness foundation for document review assistance, with code validation
  expected before AI validation.
- AI provider policy, office-level AI enablement, budget/call logging, and
  credential management foundations.
- Development fake AI provider can run real Flow/Harness paths without external
  API keys when explicitly enabled with a `fake-` provider code.
- `dev` and `local` profiles can bootstrap `fake-review` / `fake-ops`
  providers and attach fake review AI policy to existing offices, so manual UI
  testing can exercise the real AI orchestration path without a paid API key.
- Spring AI adapter is attached behind the ArchDox `AiModelGateway`; provider
  codes such as `spring-ai-*` can delegate to `flower-ai-harness-spring-ai`
  when explicitly enabled.
- Platform Admin AI management includes provider credentials, office policies,
  pricing rules, monthly usage summary, estimated cost, and recent call logs.
- Platform admin/Ops foundations separate from office admin roles.
- Platform ops workflow foundation records deterministic stuck detection as
  `platform_ops_runs`, `platform_ops_incidents`, and `platform_ops_findings`.
- Platform ops detection now runs through the `platform-ops` Flower worker;
  incident diagnosis also runs through the same worker, builds a redacted
  deterministic diagnosis snapshot, and can optionally submit `OpsDiagnosisHarness`
  to the `platform-ops-ai` worker when platform ops AI diagnosis is enabled.
- The platform admin UI can trigger incident diagnosis and show the latest
  diagnosis snapshot as operational summary, redaction policy, recent findings,
  and related operation events.
- `archdox-worker` module foundation exists for ArchDox Worker Service:
  `ArchDoxWorkerRequest`, `ArchDoxWorkerAction`, action registry, policy gate, trace events,
  and a Flower-backed `ArchDoxWorkerExecutionFlow`. `cloud-api` wires the module with a safe
  deny-by-default policy and a Flower worker slot named `archdox-worker`.
- Report-task ArchDox Worker chat now exists as the first user-facing worker slice:
  `cloud-api` persists one active worker chat session per user/project, stores messages on the
  server, submits `WORKER_CHAT_ADVANCE` through the ArchDox Worker Flower flow, and records
  worker traces through operation events. The client web app adds a `채팅` menu that requires
  a selected project and keeps the existing document/report UI as the source of truth.
  Update: this slice now uses report-task worker chat sessions rather than project-long
  chat threads. Each session stores `siteId`/`reportId` and advances deterministically
  through `AWAITING_SITE -> AWAITING_REPORT -> REPORT_WORKING` with
  `WORKER_CHAT_ADVANCE`.
  The first domain-mutating worker actions are now connected: `CREATE_SITE`,
  `CREATE_REPORT`, and `UPDATE_REPORT_STEP`. They run through the ArchDox Worker
  Flower flow, then call the existing `SiteService` and `InspectionReportService`
  paths so normal permissions and domain validation still apply. `UPDATE_REPORT_STEP`
  is now schema-aware: it resolves the selected report's workflow definition,
  exposes workflow steps to the chat UI, validates requested step codes, and
  falls back to the next unsaved workflow step when the client does not provide
  one. `SUBMIT_REPORT` is now connected as the next narrow worker action: it
  calls the existing `InspectionReportService.submit` path, runs deterministic
  submit validation, moves the report to `READY_TO_GENERATE`, advances the chat
  session to `REVIEWING`, and lets the UI guide the user to the document tab.
  Worker Chat can now continue the document path with `RUN_PREFLIGHT_REVIEW`
  and `REQUEST_DOCUMENT_GENERATION`. These actions do not create a parallel
  document process; they call the same `ReportPreflightReviewService` and
  `DocumentJobService` paths used by the document tab, then submit the existing
  Flower flows. The document tab remains the durable progress/history/download
  view even when the worker starts the work from chat. Worker Chat session
  responses now include a `workflowState` snapshot with the selected report,
  latest preflight run, latest document job, active flags, and generation
  eligibility. The web chat UI keeps polling while review or generation is
  active, and document generation controls are enabled only after a current
  revision preflight pass.
  `ConversationPlannerHarness` now exists in `archdox-ai-harness`; the
  worker chat `WORKER_CHAT_ADVANCE` path can call it after REST has returned,
  attach a typed `plannerProposal` to the assistant reply, and keep actual
  mutation behind explicit ArchDox Worker actions. The web chat UI now renders
  the latest planner proposal as a confirmation card and executes it only when
  the user clicks the normal confirmed action path.
  Current Worker execution is a single-action Flower envelope, not Bloom event
  orchestration. If Worker work grows into long-running multi-step business
  tasks, introduce an explicit `WorkerTaskOrchestrationFlow` that coordinates
  actions, deterministic validation, AI harness calls, approvals, retries, and
  recovery while keeping existing domain services as the mutation path.
  Worker Chat UI now synchronizes chat session `siteId`/`reportId` back into the
  standard selected project/site/report context, refreshes workspace lists after
  domain-mutating worker actions, and shows a lightweight processing status bar
  while pending assistant work is in progress.
- Security baseline including login protection, rate limiting, security
  response conventions, generic unexpected-error fallback, and deployment edge
  policy direction.
- Optional document signature input before generation. Signatures are
  revision-specific job input, not user profile data, and templates decide
  whether to render or ignore them.
- Local E2E bootstrap script exists at `scripts/local-e2e/start-local-e2e.ps1`.
  It starts Docker dependencies, Cloud API, ArchDox Agent, client web, and admin
  web with fake AI enabled for AWS-deployment readiness testing.

## Current Architectural Policies

Cloud API must not render documents inline. REST creates durable state and
submits orchestration. The selected ArchDox Agent renders and uploads or stores
artifacts.

All ArchDox Agents share the same runtime concept. `LOCAL_OFFICE` and
`CLOUD_MANAGED` are deployment/configuration modes, not separate product
concepts.

Agent WebSocket is the control/event channel. Large artifacts and files should
move through HTTP/object storage/storage adapters, not as large WebSocket binary
payloads.

Only registered agents may connect. One logical `agent_id` may have one active
WebSocket session. Additional physical workers need separate agent registrations.
Outbound Agent WebSocket writes are serialized through the session registry and
must not bypass it with direct `sendMessage()` calls.

The durable source of truth is the database. Flower runtime state is recoverable
from durable job/session/command records until full Flower persistence is added.

Current production/MVP operation is a single active Cloud API instance. Multi
active API operation requires command wakeup/routing and durable Flower recovery
work before it is allowed for one environment.

The document source of truth is:

```text
report snapshot + template config + output layout
```

PDF, DOCX, HTML, and future HWP outputs are render artifacts, not the data
source of truth.

AI must be optional by office/user policy and must not replace deterministic
validation. Code-level validation handles required fields, checklist completion,
photo presence, permissions, and revision gates first. AI is for higher-level
review, wording assistance, risk hints, and finding generation.

## Test Focus

When manually testing the current system, focus on:

1. Normal user flow: login -> project -> site -> report -> checklist/photo ->
   preflight review -> optional signature -> document generation -> download.
2. Agent flow: registered Agent connects, receives document commands, renders,
   uploads/completes, and handles disconnect/reconnect policy.
3. Platform admin flow: platform admin views users, offices, agents, jobs,
   failures, and operational events separately from office admin screens.
4. Permission flow: personal user, office member, office admin, and platform
   admin must not be mixed.
5. Security flow: unauthenticated and excessive requests should fail before
   expensive DB or document work.
6. Local E2E bootstrap: start the stack with `scripts/local-e2e/start-local-e2e.ps1`
   and follow `docs/testing/LOCAL_E2E_TEST_BOOTSTRAP.md`.

## Known Follow-Up Areas

- Continue UI polish and Korean label mapping across client/admin screens.
- Validate real office templates against reference forms before production use.
- Prepare deployment scripts and secrets management for Lightsail/EC2.
- Add production edge controls such as Cloudflare/WAF, Nginx limits, TLS, and
  backup/restore playbooks.
- Continue operational monitoring around stuck jobs, agent health, photo pickup,
  delivery, and AI cost/budget alerts.
- Polish the platform admin AI diagnosis view around `AI_HARNESS` findings,
  suggested actions, confidence, and operator approval flow.

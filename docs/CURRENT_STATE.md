# ArchDox Current State

Last updated: 2026-06-04

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
- ArchDox Engine Boundary direction is documented in
  `docs/architecture/ARCHDOX_ENGINE_BOUNDARY.md`. The term `ArchDox Engine API`
  refers to a future external product/API surface, while the current
  implementation should start as an internal `cloud-api` application-service
  boundary used by SaaS first. It must not be confused with `document-engine`,
  Flower's technical engine, ArchDox Agent, ArchDox Worker Service, or AI
  harness modules.
  Engine code under `cloud-api` owns typed engine request, response, finding,
  review-session, context-normalization, catalog binding, legal-reference, and
  legal-risk recipe validation objects. It must not own action registry, policy
  gate, executor, approval, or Flower execution concepts. Those belong to
  ArchDox Worker Service.
- ArchDox Engine Service strategy is documented separately in
  `docs/architecture/ARCHDOX_ENGINE_SERVICE_STRATEGY.md`. This treats the
  engine as a future standalone service/product distinct from ArchDox SaaS:
  ArchDox SaaS uses the same engine internally as a first-party, non-billable
  caller, while external Engine API/MCP consumers use the same implementation
  through authentication, quota, metering, and policy gates. Internal use is
  commercially free but still must remain observable and protected by
  operational safety limits.
- ArchDox Engine business positioning is documented in
  `docs/architecture/ARCHDOX_ENGINE_BUSINESS_POSITIONING.md`. The product should
  not be sold as generic "AI legal review." The defensible business value is
  document-to-context normalization, effective-date/source-version-fixed review,
  document/work-type/domain rule evaluation, structured findings with evidence
  and correction actions, and audit-ready review records. Final legal or
  professional judgment remains with the qualified human user.
- ArchDox MCP Gateway strategy is documented in
  `docs/architecture/ARCHDOX_MCP_GATEWAY_STRATEGY.md`. MCP is a future external
  adapter over the Engine Service, not the engine itself. External agents must
  not create ArchDox internal DTOs directly; they submit documents, raw customer
  data, extracted facts, evidence, confidence, and user answers into a context
  intake/normalization pipeline. ArchDox then builds a public canonical context,
  translates it to internal rule/review context, and calls the same Engine
  Boundary. Internal SaaS does not need MCP bootstrap or customer mapping
  profiles, but it should converge on the same canonical engine context.
  Initial context-normalization skeleton exists under
  `cloud-api/src/main/java/com/archdox/cloud/engine/context`. It is not a public
  MCP endpoint yet; it only fixes the first code-level boundary for external
  facts, normalized values, missing questions, and ambiguity candidates.
  Engine review-session REST skeleton now exists under
  `/api/v1/engine/review-sessions` with session creation, document submission,
  fact submission, normalization, skeleton validation, and result retrieval.
  It stores durable state in `engine_review_sessions` and is meant to be the
  service surface that future MCP tools wrap. A first authenticated external
  slice also exists under `/api/v1/engine/external/review-sessions`, including
  `GET /{reviewSessionId}/result` for a stable result-only response. Platform
  admins can issue/list/revoke ArchDox Engine API keys through
  `/api/v1/platform-admin/engine/api-keys`; raw secrets are returned only once
  and only secret hashes are stored. This enables early external/Codex-style
  testing of the review-session boundary, but it is not the full commercial
  Engine API yet: dedicated customer tenancy, customer plan quota, billing,
  developer portal UI, and full OAuth/MCP provisioning remain future phases.
  A first usage/audit foundation now exists in `engine_api_usage_events`.
  Successful external review-session calls record API key id, owner user,
  optional office, operation, review session id, request units, and metadata.
  Platform admins can query raw usage events and grouped summaries through
  `/api/v1/platform-admin/engine/usage/events` and
  `/api/v1/platform-admin/engine/usage/summary`. Engine API keys now also carry
  a `dailyRequestUnitLimit`; external review-session calls are blocked before
  execution if that key's daily request-unit quota would be exceeded. This is a
  key-level safety quota, not yet full customer plan billing.
  The platform admin UI now includes an Engine API Key screen for listing,
  issuing, one-time secret display, copy, and revoke operations. A first
  logged-in user connect bootstrap endpoint also exists at
  `/api/v1/engine/connect/bootstrap`. It uses `EngineConnectBootstrapWorker` to
  verify optional office membership, issue a scoped `ENGINE_REVIEW_SESSION`
  key for the current user, and return suggested MCP/API connection metadata
  for Codex, Claude, Cursor, ChatGPT, or custom agents. Self-service bootstrap
  keys now get a default TTL and cannot request an excessive future expiry.
  This is a bootstrap package, not a live MCP protocol server. External review
  sessions are isolated by `externalSessionId + ownerUserId + officeId`; an
  office-bound Engine API key cannot read another office context for the same
  owner user. `run-validation` now flows through `EngineValidationService` for
  recipe validation and returns typed `ArchDoxEngineFinding` /
  `EngineValidationResult` output.
  It also includes the first domain-backed review slice: if external context
  supplies `tradeCode`, `processCode`, and `inspectionItemCode`, the Engine
  validates the selection against `SupervisionDomainCatalogService`, returns
  official catalog binding metadata on success, and returns typed findings such
  as `CATALOG_SELECTION_INVALID` on mismatch. If matching active
  `legal_domain_bindings` rows exist, validation metadata now also includes
  top-level typed `legalReferences` with act/article/source-version context. This is the first
  source-backed legal reference bridge; it is not yet full legal compliance
  judgment. The Engine now also runs a deterministic legal-risk context recipe:
  when legal references exist but supervision narrative/work area/photo/evidence
  context is missing, it emits `LEGAL_EVIDENCE_CONTEXT_MISSING` and prepares
  `metadata.legalRiskReview.aiPromptContext` for future legal-review harness
  steps. If that result should trigger real work before a real Worker action
  exists, the Engine returns typed non-worker `nextActions` instead of pretending that
  a future action is executable. `suggestedWorkerActions` must name only actions
  currently present in the Worker registry with executor, policy, and tests.
  Engine must not execute controlled actions itself. The first bridge now
  exists as `EngineWorkerActionSuggestionBridge` under
  `cloud.worker.engine`: it turns Engine suggestions into
  `EngineWorkerActionCandidate` metadata for traceability and future Worker
  execution planning, but it does not execute anything. The first execution
  handoff also exists as `EngineWorkerActionSubmissionService` under the same
  Worker-side package. It submits only runnable candidates into
  `ArchDoxWorkerExecutionFlowFactory`, so execution still passes Worker resolve,
  policy, Flower, and trace recording. It skips recursive actions such as
  `RUN_PREFLIGHT_REVIEW` inside an already-running preflight flow and skips
  Worker-Chat scoped executors when chat session payload is missing.
  Internal SaaS preflight has also started using this same Engine Boundary:
  `ReportPreflightReviewFlowService` keeps the existing deterministic report
  validator, then calls `ReportPreflightEngineBoundaryService` to build a
  DB-backed normalized context from `DAILY_LOG.dailyItems`, report revision,
  site/project ids, and photo evidence metadata. That context runs through
  `EngineValidationService`. Engine findings are merged into the existing
  `report_preflight_review_findings` table so the document tab and Worker Chat
  still see one preflight result stream.
- Public site/domain strategy is documented in
  `docs/architecture/PUBLIC_SITE_AND_DOMAIN_STRATEGY.md`. The preferred product
  direction is that `archdox.co.kr` becomes the public product/onboarding site
  for ArchDox Engine and MCP Gateway, while the existing SaaS work app moves to
  `app.archdox.co.kr` and the admin app to `admin.archdox.co.kr`. Future hosts
  include `api.archdox.co.kr` for Cloud API and `mcp.archdox.co.kr` for MCP.
  Host-based routing on the same MVP server is acceptable; separate servers can
  wait until traffic, isolation, or deployment cadence requires it. Public Site
  V1 static pages now exist under `public-site/`, including an ArchDox
  green-themed technical Engine/MCP landing page at `/`, plus `/connect/`,
  `/developers/mcp/`, and `/legal-updates/`. The Lightsail/Caddy/Nginx
  configuration is prepared for
  `archdox.co.kr`, `app.archdox.co.kr`, `admin.archdox.co.kr`,
  `api.archdox.co.kr`, and placeholder `mcp.archdox.co.kr` host routing.
  Public CTAs now use explicit SaaS auth routes:
  `https://app.archdox.co.kr/signup` and
  `https://app.archdox.co.kr/login`; the client app opens the matching
  AuthScreen mode from those paths. `mcp.archdox.co.kr` currently redirects to
  public MCP documentation instead of pretending to be a live protocol endpoint.
- Legal domain direction is documented in
  `docs/architecture/LEGAL_DOMAIN_ARCHITECTURE.md`. The first implementation
  should live inside `cloud-api` as an isolated legal domain package with
  DB-backed legal corpus, legal sync/diff Flower flows, and platform admin
  visibility. It must be designed so it can later be extracted into
  `archdox-legal-worker` if legal sync, diff, indexing, or AI impact analysis
  becomes heavy enough to justify a separate process.
  Initial implementation has started with `V42__legal_domain_foundation.sql`,
  `V43__legal_change_digests.sql`, fake legal source sync, official National
  Law Open API sync, legal text normalization/hash, article diff detection,
  `LegalSyncFlow`, `LegalSyncWorker`, and platform-admin backend endpoints for
  sync/readback. Official API sync is disabled unless `LEGAL_OPEN_API_ENABLED`
  and `LEGAL_OPEN_API_OC` are configured. It tracks only construction
  supervision MVP assets and throttles calls with
  `LEGAL_OPEN_API_REQUEST_INTERVAL_MS` / `LEGAL_OPEN_API_MAX_ATTEMPTS`.
- Site supervision ledger foundation exists: construction daily log `DAILY_LOG`
  saves now synchronize structured trade/process/inspection-item/photo
  observations into `site_supervision_entries`, and report submission promotes
  the matching report revision entries from `DRAFT` to `CONFIRMED`. Future
  trade-specific checklist and supervision-report screens should reuse this
  ledger rather than scraping another document's step payload.
- Daily log ledger projection is now catalog-bound: selected
  trade/process/check item codes are validated against the construction
  supervision domain catalog, official catalog names are stored in the ledger,
  and the catalog JSON version is the current MVP version source.
- Construction daily log ledger projection requires a site context. Missing
  `siteId` is surfaced through submit validation and blocked during ledger
  projection instead of being silently skipped.
- Construction supervision domain assets are currently managed as code-reviewed
  JSON catalogs and exposed through Cloud API. The planned production direction
  is to publish stable catalog revisions into DB, keep code JSON as seed/source
  material, and store office-specific catalog overrides as separate audited DB
  revisions after the base structure stabilizes.
- Because ArchDox is still pre-production, construction daily supervision
  payload compatibility aliases were intentionally removed. `DAILY_LOG`
  `dailyItems.groups[]` now uses canonical keys only:
  `tradeCode`, `tradeName`, `processCode`, `processName`, `floor`, and
  `entries[]` with `inspectionItemCode`, `inspectionItemName`,
  `supervisionContent`, and `photoIds`.
- Current active business scope is **construction supervision** only. Fresh
  database seed migrations create only `CONSTRUCTION_DAILY_SUPERVISION_LOG`
  and `CONSTRUCTION_SUPERVISION_REPORT`; legacy safety/facility/demo document
  types are no longer inserted and then removed. Migration
  `V40__remove_deferred_document_types.sql` exists only as a compatibility
  cleanup for dev/pre-production databases that already consumed the earlier
  broad exploratory seeds. Demolition reference forms may remain as archived
  source material, but they must not appear in new report creation until a
  separate demolition-supervision phase is explicitly opened.
- Preflight document review gate tied to report revision state.
- The document-making worker line is now centered on the existing
  `ReportPreflightReviewWorker` before render request submission. For the
  construction daily supervision log MVP, deterministic preflight now checks
  the structured `DAILY_LOG.dailyItems` payload before AI review: trade/process,
  floor, inspection item, supervision content, and recommended photo evidence.
  This keeps code-checkable document quality issues out of paid AI calls and
  lets the AI harness focus on wording, consistency, and compliance risk.
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
  The worker action registry now has `ArchDoxWorkerActionDefinition` metadata
  for owner, executor name, enabled state, read/write classification, risk
  level, allowed sources, required context fields, approval default, and dry-run
  support. Cloud API worker policy reads those definitions instead of carrying a
  separate hardcoded enabled-action list.
  Platform admin can now inspect Worker governance metrics through
  `/api/v1/platform-admin/ops/worker-governance` and the admin UI
  `Worker 통제` view. This view does not create a separate raw metrics table:
  it aggregates existing `operation_events` where `workflow_type =
  'archdox-worker'`, limits the query window to at most 30 days, and returns
  only bounded recent trace samples. Current metrics include catch rate
  (denied/rejected/unknown actions per request), approval-required rate,
  failure rate, event distribution, reason distribution, and action trace
  distribution. False-positive, override, and replay-success metrics are future
  approval/replay features and must not be faked before those flows exist.
  A first Worker approval foundation now exists through
  `worker_approval_requests` and
  `/api/v1/platform-admin/ops/worker-approvals`. When the Worker policy gate
  returns `REQUIRE_APPROVAL`, Cloud API turns the Worker trace into one pending
  approval request. Platform admins can list, approve, or reject those requests
  in the admin `Worker 승인` view. Approval does not mutate the domain object
  directly: it creates a fresh approved Worker execution request, stamps the
  approval id into the action payload, and submits the same Flower-backed
  `ArchDoxWorkerExecutionFlow` again. The policy gate only bypasses the
  approval stop when the approval record is `APPROVED`, the execution request id
  matches, the action type matches, and the Worker context matches. This table
  is approval state, not a raw metrics/event table.
- Report-task ArchDox Worker chat now exists as the first user-facing worker slice:
  `cloud-api` persists one active worker chat session per user/project, stores messages on the
  server, submits `WORKER_CHAT_ADVANCE` through the ArchDox Worker Flower flow, and records
  worker traces through operation events. The client web app adds a `梨꾪똿` menu that requires
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
- Document generation request submission is now centralized through
  `DocumentGenerationRequestService`. The document tab REST path and Worker
  Chat's `REQUEST_DOCUMENT_GENERATION` path both create the durable
  `DocumentJob` through the same boundary and submit the same
  `DocumentGenerationWorker` Flower flow after transaction commit. This keeps
  the controlled worker action boundary separate from the document render
  recipe flow and prepares the path for future `flower-agent-runtime`
  style planner/policy/approval layers.
- Security baseline including login protection, rate limiting, security
  response conventions, generic unexpected-error fallback, and deployment edge
  policy direction.
- Optional document signature input before generation. Signatures are
  revision-specific job input, not user profile data, and templates decide
  whether to render or ignore them.
- Local E2E bootstrap script exists at `scripts/local-e2e/start-local-e2e.ps1`.
  It starts Docker dependencies, Cloud API, ArchDox Agent, client web, and admin
  web with fake AI enabled for AWS-deployment readiness testing.
- Legal domain foundation exists inside `cloud-api` as an isolated package.
  It stores legal sources, acts, versions, articles, article diffs, change sets,
  and published change digests. Platform admins can trigger a fake legal sync
  and inspect sync runs/change sets/digests. Authenticated users can read recent
  published legal update digests through the client web legal update screen.
  The current source connector is fake/static until National Law Open API
  credentials are approved. Future notification fan-out should trigger from
  published digest rows, not directly from raw article diffs.

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

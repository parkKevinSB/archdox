# Agent Rules

This document defines rules that AI development agents must follow when working
on ArchDox. Keep changes conservative, tenant-safe, and consistent with the
current architecture.

## Core Development Rules

1. Read `AGENTS.md` before making changes.
2. Preserve the multi-module boundary:
   - `cloud-api` owns HTTP APIs, auth, tenancy, persistence, orchestration.
   - `archdox-agent` owns the ArchDox Agent runtime. It can run as
     `LOCAL_OFFICE` for office/NAS execution or later as `CLOUD_MANAGED` for
     cloud document generation.
   - `document-engine` owns reusable document generation primitives.
   - `domain-shared` owns small shared enums/value helpers only.
3. Keep controllers thin. Put business rules in application services.
4. Keep repositories scoped to a single aggregate or clear read model.
5. Prefer existing package style and naming over new abstractions.
6. Do not introduce a new framework or runtime without explicit approval.
7. Do not store secrets, API keys, passwords, or private certificates in the
   repository.
8. Use environment variables or external secret stores for runtime secrets.
9. Follow `docs/development/DDD_EVENT_ORCHESTRATION_RULES.md` for DDD,
   Bloom-based internal events, and Flower-based orchestration.
10. Follow `docs/architecture/ARCHDOX_AGENT_ARCHITECTURE.md` for Agent naming,
    deployment modes, DB table names, and storage profile rules.
11. Follow `docs/architecture/CLOUD_API_SCALING_AND_ROUTING.md` for load
    balancing, stateless REST APIs, and multi-instance Agent command routing.
12. Follow `docs/architecture/OPERATIONS_AND_ADMIN.md` when adding admin,
    monitoring, logging, audit, health check, or Ops Agent features.
13. Follow `docs/architecture/ARCHDOX_PLATFORM_IDENTITY.md`: ArchDox is a
    document workflow orchestration platform, not a narrow document generator.
14. Follow `docs/architecture/FLOW_RECOVERY_POLICY.md` when changing Flower
    flows, Agent commands, startup recovery, or restart behavior.
15. Follow `docs/architecture/CONFIGURATION_BASED_CUSTOMIZATION.md` when adding
    customer-specific templates, report behavior, workflow stages, validation
    rules, output layouts, or office overrides.
16. Follow `docs/architecture/DOCUMENT_NEUTRAL_MODEL.md` when adding document
    generation data, template bindings, output layout behavior, artifact
    formats, AI document review, or report snapshot changes.
17. Follow `docs/architecture/CLIENT_PRODUCT_UI_DIRECTION.md` when implementing
    real user-facing `client/web` screens. Share the ArchDox admin visual system
    and theme, but keep normal user workflows simpler than admin/Ops screens.
18. Follow `docs/architecture/FRONTEND_STACK_DECISION.md` before adding or
    replacing major frontend libraries, routers, UI kits, validation libraries,
    or state-machine dependencies.
19. Follow `docs/architecture/FRONTEND_ARCHITECTURE.md` when adding or
    refactoring `client/web` or `admin` React code. Keep modules feature-shaped:
    `App` composes, `features/*` owns business UI, and `components/*` owns
    reusable product UI only. For normal user screens, preserve the
    workflow-composed UI model: project -> site -> report -> step/photo -> document.
    Larger devices may show more context, but must not create a different
    business journey from mobile. Follow
    `docs/architecture/UI_WORKFLOW_ORCHESTRATION.md` when changing report
    writing, photo capture, document progress, or other guided user workflows.
20. For non-trivial React features, keep REST calls in `features/*/api.ts`,
    feature-local request/response types in `features/*/types.ts`, server state
    in TanStack Query hooks, and multi-field form state in React Hook Form.
    Do not force this pattern onto tiny UI-only components, but apply it before
    a feature grows enough to mix API calls, permissions, loading state, and
    JSX in one file.
21. Major user workflow areas in `client/web` should be represented by React
    Router paths. Do not add new top-level pages that are reachable only through
    hidden local component state.
22. Follow `docs/architecture/SITE_TARGET_HIERARCHY.md` when adding projects,
    sites, buildings, facilities, inspection targets, reports, photos, or
    document binding context. `site` is `현장`; `project` is the larger
    business container that may contain many sites.
23. Do not add new Spring `@Scheduled` jobs, cron jobs, polling loops, or
    scheduler-style background mechanisms without explicit user confirmation.
    If the behavior is orchestration with waiting, retry, timeout, or backoff,
    design it as a Flower flow/worker first.
24. Follow `docs/development/GIT_WORKFLOW.md` for branch names, commit messages,
    PR expectations, CI checks, and files that must never be committed.

## Tenant And Security Rules

1. Every office-owned business row must be isolated by `office_id` directly or
   through a parent row that is checked by `office_id`.
2. User JWTs identify the user. The active office is selected per request with
   `X-Office-Id`, except office management APIs that explicitly use
   `/offices/{officeId}`.
3. Any API that reads or mutates office business data must verify active
   membership before accessing data.
4. Cross-office access must return `403` when the user is not a member of the
   requested office and `404` when a resource does not exist inside the active
   office.
5. Do not join by ID alone for office-owned resources. Include `office_id` in the
   lookup or verify ownership through the parent aggregate.
6. Audit-sensitive actions should be prepared for audit logging even when the
   first MVP endpoint does not yet write a full audit row.
7. Signup must not silently create mixed personal/office identities. `PERSONAL`
   signup creates a personal workspace. `OFFICE` signup must validate
   `officeCode + invitationToken + matching email` and must not create a
   personal workspace.
8. Invitation links must open a dedicated signup flow, even if the browser
   already has a logged-in user. The invite preview may prefill office code,
   office name, and invited email, but the invitee must not be allowed to switch
   the target office from that screen.
9. Production office signup must add email verification before activation; until
   then, invitation tokens are treated as one-time secrets.
10. Do not rely on UI-only permission checks. Cloud API must enforce the same
    office role rules documented in `DOMAIN_MODEL.md`: project/site/target
    creation requires personal `OWNER` or office `OWNER`/`ADMIN`; site/target
    structure may also allow project `MANAGER`; report writing requires personal
    `OWNER` or office `OWNER`/`ADMIN`/`MEMBER` unless project/report assignments
    narrow access; `VIEWER` is read-only.

## Document Generation Rules

1. Do not place document composition logic in `cloud-api` controllers.
2. Shared document generation logic belongs in `document-engine`.
3. `cloud-api` may create jobs, validate ownership, store artifacts, and
   orchestrate delivery.
4. `archdox-agent` may execute jobs, access configured storage, call
   `document-engine`, and report results.
5. If cloud-managed and office-installed agents need the same generation behavior, add or extend a
   `document-engine` contract rather than copying code.
6. Support `CLOUD_ENCRYPTED` as the default draft mode and preserve the path for
   `LOCAL_ONLY` offices.
7. Document generation must be modeled as an asynchronous job/flow. REST creates
   the job and returns a job response immediately; progress is read by polling
   the job detail API.
8. Use one render flow contract for both office and personal plans. Office
   plans route to `ARCHDOX_AGENT`; personal plans route to the `CLOUD` document
   worker/agent path.
9. Cloud API owns job state, tenant checks, progress, and artifact metadata.
   Render workers own execution and report ACK/completion/failure.
10. ArchDox Agent document rendering uses the `GENERATE_DOCUMENT` command type.
    WebSocket messages should carry command status only; artifact binaries stay
    in ArchDox Agent/NAS storage unless a delivery flow explicitly uploads or
    shares them.
11. Document download APIs may directly stream only `API_LOCAL` artifacts.
    `ARCHDOX_AGENT` artifacts require a delivery request and a later ArchDox Agent
    delivery/upload/share flow.
12. Agent-backed document delivery uses `UPLOAD_DOCUMENT_ARTIFACT`. The Agent
    uploads a prepared Cloud copy for a specific delivery request; Cloud must not
    expose the Agent/NAS path directly.
13. Agent-backed document delivery orchestration must use the
    `document-delivery` Flower flow. Delivery retry/backoff and terminal
    failure decisions belong in Flower steps.
14. Photo original pickup for office `CLOUD_MEDIATED` uploads must use the
    `photo-pickup` Flower flow. The ArchDox Agent command service may create
    and send `PHOTO_PICKUP` commands, but retry/backoff, timeout, and final
    pickup failure decisions belong in the Flower step.
15. Do not hard-code office-specific document layouts, labels, templates, photo
    table shapes, or approval behavior in Java branches. Resolve versioned
    templates/configuration and pass the result to `document-engine` or the
    Flower flow.
16. Report submission readiness is a Cloud API responsibility. The UI may guide
    the user, but `cloud-api` must validate required steps, checklist state, and
    working photo availability before moving a report to `READY_TO_GENERATE`.
17. Generated document artifacts are immutable history for a specific
    `reportRevision`. Editing a submitted/generated report must reopen it as a
    new `contentRevision`; do not mutate or silently replace prior artifacts.
18. The source of truth for document generation is the neutral document snapshot
    in `document_jobs.input_snapshot_json`, not DOCX, HTML, PDF, HWP, or any
    customer-specific artifact.
19. Add new document data to the neutral snapshot first, then expose it through
    `templateFields`, `layoutSections`, or bounded renderer/exporter behavior.
20. Common public-form placeholders may be supplied by
    `StandardTemplateFieldResolver`, but they must remain report-domain neutral.
    Do not use it for office-specific layouts or customer-specific branches.
    Explicit template schema bindings override these standard defaults.
21. Output formats are artifact targets. DOCX/HTML/PDF/HWP/HWPX-specific code
    must stay inside document-engine renderers/exporters or deployment-specific
    converter adapters.
22. Configured template revisions are content-required render inputs. If the
    selected revision's DOCX content is missing or unreadable, fail the document
    job clearly; do not silently generate a different fallback document.

## Configuration And Customization Rules

1. Customer-specific business variants must be configuration candidates before
   they become code branches.
2. Branching on `officeId` is allowed for tenant isolation and authorization
   checks, but not for customer-specific business behavior such as layout,
   labels, approval stage count, required fields, or storage preference.
3. Branching on `reportType` is allowed when selecting a typed domain policy or
   configuration key. Avoid embedding every report-type layout or checklist
   difference directly in service code.
4. Version templates, workflow definitions, rule sets, and output layout
   configurations. Existing reports/jobs must keep the selected revision or an
   immutable input snapshot.
5. Keep configuration DSLs narrow. JSON config may select supported behavior,
   but must not execute arbitrary code, SQL, or unrestricted service calls.
6. Core security, tenant isolation, command authentication, and data ownership
   invariants must remain code-level guarantees, not editable configuration.
7. Configuration resolution should be explainable: record whether the selected
   config came from an office override, plan/report-type default, or system
   default.
8. Admin UI may later edit configuration, but publication should be explicit and
   revisions should be immutable after publication.

## Event And Orchestration Rules

1. Use Bloom for module-internal runtime events.
2. Use Flower for orchestration with steps, waiting, retry, timeout, or
   backoff.
3. Domain events should be immutable Java records and named as business facts.
4. Flower steps should orchestrate only. Keep domain rules in domain/application
   services.
5. Prefer explicit Flower flow ids, step ids, and `stepNo` over hidden state in
   ad-hoc scheduler code.
6. HTTP/REST entrypoints must preserve request/response responsibility. Do not
   start a user command by only publishing a Bloom event and hoping another
   component responds later.
7. For long-running REST commands, create the command/job state in an
   application service, submit the Flower flow explicitly, and return the
   current command/job response.
8. Bloom events belong inside module/runtime boundaries: domain facts,
   projection updates, Flower step signaling, or `@Subscribe` handlers for
   internal follow-up work.
9. Spring-level Bloom handlers must use `bloom-spring` `@Subscribe`; avoid
   manual `eventBus.subscribe(...)` subscriber classes in Spring beans.
10. A Flower flow can be long-lived on a worker. Do not replace long-lived flow
    progression with Spring scheduling just because the process needs to wait
    across time.
11. New scheduler-style code requires explicit user confirmation. The default
    choice for orchestration is Flower.

## Image Upload And Storage Rules

1. Follow `docs/architecture/IMAGE_UPLOAD_POLICY.md` for all photo upload and
   storage decisions.
2. Office plan originals must not be stored long-term in cloud by default.
3. Office plan upload default is `CLOUD_MEDIATED`: client uploads to temporary
   object storage, ArchDox Agent pulls, NAS/agent storage keeps the original, and
   the temporary cloud original is deleted after pickup.
4. `ARCHDOX_AGENT_DIRECT` is an advanced option only when LAN, managed tunnel, VPN,
   Tailscale, Cloudflare Tunnel, or equivalent connectivity exists.
5. Cloud may keep metadata, thumbnails, and optional working images for preview
   and document generation.
6. Document generation should use working images, not full originals.
7. Personal plans may use cloud object storage because they do not have an
   office-installed ArchDox Agent/NAS path; original retention must be controlled
   by plan/policy.
8. Store logical `storage_ref` keys only. Do not store NAS absolute paths in
   Cloud DB.
9. User-facing photo UI must follow the upload intent contract: create intent,
   upload to the returned URL, confirm upload, then observe asset/pickup status
   through API state.
10. User-facing photo preview may fetch `THUMBNAIL` and `WORKING` content with
    `Authorization` and `X-Office-Id`, then render a browser object URL. Do not
    expose `ORIGINAL` content through normal browser preview UI.
11. Personal external drives such as Google Drive or MYBOX are optional original
    backup providers only. Do not make document generation depend on external
    drive originals.

## Deployment Portability Rules

1. ArchDox must not be coded as AWS-only or local-server-only. Follow
   `docs/architecture/DEPLOYMENT_PORTABILITY.md`.
2. Application/domain code must not depend directly on AWS, Tailscale, MinIO,
   NAS paths, or OpenAI provider details. Use ports such as `StorageService`,
   `AiTextGenerationService`, and Agent connection properties.
3. Store logical storage metadata only. Do not persist absolute local or NAS
   filesystem paths in business records.
4. Agent WebSocket URLs must be configuration values. Do not hardcode public
   domains or Tailscale addresses in Java code.
5. Secrets must come from environment variables or a secret store, not committed
   profile YAML.
6. Docker Compose local operation should remain deployable with PostgreSQL,
   MinIO, MailHog, optional Cloud API/ArchDox Agent containers, and optional
   Ollama. Do not make local development depend on AWS-only services.

## API Rules

1. Public API paths use `/api/v1`.
2. Internal agent/admin paths must be explicitly named and documented before use.
3. Request and response DTOs must be Java records unless there is a strong reason
   otherwise.
4. DTO names must end with `Request`, `Response`, or `Command`.
5. API behavior changes must update `docs/architecture/API_CONTRACT.md`.
6. Do not expose JPA entities directly from controllers.
7. User-facing REST APIs must be stateless at the API instance level. Store
   durable state in DB/queue, not in one API server's memory.
8. Cloud API instances must not call each other directly for coordination. Use
   DB, Redis Pub/Sub, Postgres NOTIFY, or a queue.
9. Agent WebSocket session memory is a transport cache only. Command truth must
   remain in `archdox_agent_commands`.
10. Agent WebSocket connection visibility belongs in `archdox_agent_sessions`.
    Prefer ACTIVE session rows when choosing a command target, but never depend
    on memory alone.

## Operations And Admin Rules

1. Admin UI must call Cloud API admin endpoints. It must not bypass
   authorization or read the database directly.
2. Platform admin access is separate from office membership roles.
3. Cross-office admin APIs must require explicit platform admin authorization.
4. Important operational facts belong in structured operation/audit records, not
   only raw logs.
5. Workflow events that matter for support or recovery should create
   `operation_events` with `workflow_type`, `workflow_key`, `resource_type`, and
   `resource_id` when those values exist.
6. Raw logs and `operation_events.payload_json` must not contain secrets,
   signed URLs, passwords, device secrets, install tokens, or file contents.
7. Prometheus/Micrometer metrics are useful for trends, but DB-backed admin
   views remain the source for exact job, command, Agent, and office state.
8. The future ArchDox Ops Agent is separate from ArchDox Agent. It should start
   as read-only monitoring/reporting and should not directly delete live logs or
   mutate business data.
9. Office-scoped ops read APIs must require office `OWNER` or `ADMIN`. They must
   not expose signed URLs, command payloads, device secret hashes, install
   tokens, or raw file contents.
10. The `admin` React app is an operations console, not a marketing site. Keep
    it dense, quiet, table-oriented, and read-only until workflow repair actions
    are explicitly designed.
11. Admin frontend API calls must go through Cloud API HTTP endpoints with
    normal authorization headers and `X-Office-Id`; do not read databases or
    internal files directly from the UI.

## Testing Rules

1. Add or update tests for new business behavior.
2. Add tenant isolation tests for every new office-owned aggregate.
3. Prefer unit tests for pure logic and integration tests for HTTP, security,
   tenant boundaries, persistence, and migrations.
4. If Docker is unavailable and a Testcontainers test is skipped, report that
   clearly.
5. Do not claim a feature is fully verified if only compilation was run.
6. Before extending document worker routing, keep the document job integration
   test green: create returns `QUEUED/0%`, background generation completes, and
   polling returns `GENERATED/100%` with artifact metadata.

## Completion Rules

Before reporting work as complete:

1. Compile the touched modules.
2. Run the most relevant test task.
3. State exactly what was changed.
4. State tests run and any skipped or blocked tests.
5. State known limitations or follow-up work.

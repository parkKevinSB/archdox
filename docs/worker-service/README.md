# ArchDox Worker Service Direction

This document captures the intended direction for ArchDox as a worker-based
service. It merges the controlled agent runtime idea from
`flower-agent-orchestration` with the product direction discussed for ArchDox.

This is a direction document, not an implementation ticket. The goal is to
guide the next ArchDox design/implementation chat.

## Core Thesis

ArchDox should not remain only a traditional document SaaS where users click
through forms, templates, and buttons.

ArchDox should grow into a domain-specific AI worker service for construction
supervision and document workflows.

In short:

```text
ArchDox is not a generic AI worker platform.
ArchDox provides specialized AI workers for supervision/document work.
```

This fits the broader Flower ecosystem philosophy:

```text
AI proposes.
Policy validates.
Flower executes.
```

The point is not to make AI freely autonomous. The point is to make AI workers
operable inside real business workflows with permission checks, approval,
audit, recovery, cost control, and UI-visible state.

The user experience should move from:

```text
User operates a document tool.
```

to:

```text
User delegates document/supervision work to ArchDox workers,
then reviews, edits, approves, and submits the result.
```

## Product Direction

ArchDox should support both modes:

```text
1. Existing UI-based document workflow
   - document lists
   - report/template editing
   - finding review
   - manual corrections
   - approval/finalization
   - artifact delivery

2. Worker-based command workflow
   - "Draft today's inspection log."
   - "Review this report for missing evidence."
   - "Check legal/contract risks."
   - "Prepare the owner-submission version."
   - "Find missing photos or required attachments."
```

These are not separate products. They must share the same domain model,
workflow state, document jobs, findings, approvals, and audit records.

The chat or command UI is only an entry point. The existing UI remains the
workspace for inspection, correction, approval, and operational control.

```text
Chat/command UI
  = ask ArchDox workers to do work

Existing UI
  = review, edit, approve, manage, submit, and audit the work
```

## Worker Service, Not Generic Platform

Do not build a general-purpose worker marketplace or generic AI automation
platform.

ArchDox workers should be specialized for the domain:

```text
InspectionLogWorker
  - drafts supervision logs from site/project/report context

ReportDraftWorker
  - drafts report sections from structured data, photos, and templates

DocumentQaWorker
  - reviews document completeness, consistency, and formatting

LegalReviewWorker
  - checks contract/legal/rule risks where configured

EvidenceGapWorker
  - finds missing photos, attachments, measurements, or required proof

SubmissionPackageWorker
  - prepares final delivery/submission bundles

ReviewPlannerWorker
  - proposes the next allowed worker action based on findings and state
```

These workers are product capabilities of ArchDox, not a public generic
platform layer.

## Runtime Principle

The controlled runtime rule:

```text
AI proposes the next action.
Policy and registry decide whether it is allowed.
Flower executes the workflow.
ArchDox records the result.
```

The AI planner's output is data, not authority. It should never directly
execute arbitrary code, mutate workflow graphs freely, or write to the database.

Every executable action must be registered, typed, policy-checked, traceable,
and recoverable.

## Implementation Readiness

The generic `flower-agent-orchestration` framework should not be implemented
yet.

The right next step is to implement a small ArchDox-owned worker runtime slice
inside ArchDox and validate the pattern with real document/supervision work.

```text
Implement now inside ArchDox:
  WorkerActionRegistry
  WorkerPolicyGate
  WorkerExecutionFlow
  minimal WorkerAuditEvent
  DocumentQaWorker
  ReviewPlannerWorker

Do not implement yet as a generic framework:
  flower-agent-orchestration
  enterprise worker operations platform
  generic worker marketplace
  full AI governance/compliance suite
```

Reason:

```text
ArchDox knows the real domain rules, UI states, approvals, permissions,
document objects, report lifecycle, and failure cases.
```

Only after these patterns repeat without ArchDox-specific language should they
be extracted into a generic Flower ecosystem module.

## Relationship To Flower And Harness

```text
Flower
  = general flow/step execution engine

Flower worker
  = technical runtime slot that ticks Flower flows

flower-ai-harness
  = makes one AI call/task reliable:
    prompt, model call, validation, retry/refine, fallback, findings,
    cancellation, snapshot/recovery, fake-provider tests

ArchDox worker orchestration
  = ArchDox-internal orchestration of domain workers and allowed actions

future flower-agent-orchestration
  = optional extraction of domain-neutral controlled-agent patterns,
    only after ArchDox proves repeated needs
```

## Module Boundary Quick Map

```text
flower-ai-harness
  = generic framework for one reliable AI task

archdox-ai-harness
  = ArchDox-specific AI task definitions using flower-ai-harness

archdox-worker
  = controlled ArchDox action orchestration layer

archdox-agent
  = registered document/photo/artifact execution runtime
  = not an AI agent
```

Do not add ArchDox worker concepts into Flower.

Do not add multi-worker planning/orchestration into `flower-ai-harness`.

Do not create `flower-agent-orchestration` as a generic framework first.
Build and validate the worker pattern inside ArchDox first.

## Terminology Rule

Use the following terms explicitly:

```text
Flower worker
  = Flower runtime execution slot/thread that ticks flows

ArchDox Worker
  = ArchDox domain/product capability that performs supervision/document work

ArchDox Agent
  = registered execution runtime for document/photo/artifact commands
```

Do not use bare `Worker` in architecture documents when it can be confused with
Flower's runtime worker. In Java code, ArchDox domain worker types use the
`ArchDoxWorker*` prefix, for example:

```text
ArchDoxWorkerRequest
ArchDoxWorkerAction
ArchDoxWorkerActionRegistry
ArchDoxWorkerPolicyGate
ArchDoxWorkerExecutionFlowFactory
```

## Existing ArchDox Agent Naming Boundary

ArchDox already has an official `ArchDox Agent` concept.

Use the terms carefully:

```text
ArchDox Agent
  = registered execution runtime for document/photo/artifact commands
    in LOCAL_OFFICE or CLOUD_MANAGED deployment mode

ArchDox Worker Service
  = product capability where users delegate supervision/document tasks
    to domain-specific AI-assisted workers

Worker orchestration
  = Cloud/API/domain workflow layer that decides, validates, and coordinates
    worker actions through Flower flows
```

The worker service may use ArchDox Agent for execution when storage, rendering,
or office-local resources are needed. But "Agent" and "Worker" should not be
collapsed into the same domain term.

## Implementation Boundary

The first ArchDox implementation module is named:

```text
archdox-worker
```

Use this name for the Gradle module and internal Java package. Use
`ArchDox Worker Service` as the product/design name in documents and UI copy.

Do not name the module `archdox-worker-orchestration`; orchestration is one of
the module responsibilities, but the product concept is broader: specialized
domain workers for supervision/document work.

Initial runtime shape:

```text
cloud-api
  -> owns REST/auth/permissions/current domain APIs
  -> submits ArchDox worker flows when a worker action is requested

archdox-worker
  -> owns ArchDoxWorkerRequest, ArchDoxWorkerAction, registry, policy gate, trace event,
     and ArchDoxWorkerExecutionFlow
  -> does not own UI state or separate hidden product state

existing client/admin UI
  -> continues to display and modify the normal ArchDox domain objects
```

The worker module starts as an in-process library used by `cloud-api`. It may
become a separate deployable `archdox-worker-service` process later only if
load, scaling, or operational isolation requires it.

The first implementation is intentionally safe by default:

```text
- worker action registry exists
- worker policy gate exists
- Flower-backed ArchDoxWorkerExecutionFlow exists
- cloud-api has a Flower worker slot named archdox-worker
- no domain action executor is registered by default
- cloud-api default policy denies worker actions until explicit features are added
```

This keeps the existing UI/document workflow untouched while preparing the next
slice: registering real domain workers such as `DocumentQaWorker` and
`ReviewPlannerWorker`.

## Suggested Internal Architecture

```text
User command / chat / UI action
  -> Worker request
  -> Intent or planner step
  -> ArchDoxWorkerAction proposal
  -> ArchDoxWorkerActionRegistry lookup
  -> ArchDoxWorkerPolicyGate
  -> optional approval gate
  -> Flower-backed ArchDoxWorkerExecutionFlow
  -> execute domain worker action
  -> flower-ai-harness for AI task calls
  -> persist findings/results/artifacts
  -> update UI-visible workflow state
  -> audit trace
```

Candidate internal components:

```text
ArchDoxWorkerRequest
  - user/office/project/site/report context
  - natural language command or UI-originated command
  - target document/report/job references

ArchDoxWorkerAction
  - stable action id
  - typed input payload
  - reason
  - confidence
  - proposed by: user, planner, system

ArchDoxWorkerActionRegistry
  - maps allowed action ids to executors
  - exposes planner-safe action descriptions
  - rejects unknown actions

ArchDoxWorkerPolicyGate
  - checks user permission
  - checks office/project/report state
  - checks current workflow stage
  - checks budget/loop limits
  - checks whether approval is required

ArchDoxWorkerExecutionFlow
  - Flower flow that executes a selected and allowed action
  - owns waiting, retry, timeout, recovery, and status transitions

ArchDoxWorkerTrace / ArchDoxWorkerAuditEvent
  - records command, planner proposal, policy result, action execution,
    generated findings/artifacts, approval decisions, and failures
```

## Planner Pattern

The planner should choose only from a fixed list of allowed actions.

Example:

```text
Available actions:
  RUN_DOCUMENT_QA
  RUN_LEGAL_REVIEW
  RERUN_DOCUMENT_QA_WITH_STRONGER_MODEL
  DRAFT_INSPECTION_LOG
  REQUEST_HUMAN_REVIEW
  PREPARE_SUBMISSION_PACKAGE
  APPROVE_DOCUMENT
  FAIL_REVIEW

Planner input:
  current report/document state
  document QA findings
  legal review findings
  existing photos/evidence
  user command
  previous worker action history

Planner output:
  action: RERUN_DOCUMENT_QA_WITH_STRONGER_MODEL
  reason: "QA and legal review disagree on required clause coverage."
```

The planner result must be checked:

```text
Is this action registered?
Is it allowed in the current document/report state?
Is the user allowed to request or approve it?
Is the loop/budget still available?
Does it require human approval?
Should it be escalated instead of executed?
```

Only after those checks should Flower execute the action.

## UI And Worker Must Share State

The worker service must not create a second hidden product.

Bad direction:

```text
Chat has its own state.
UI has separate document state.
Worker output is just text in chat.
```

Correct direction:

```text
Worker creates/updates normal ArchDox domain objects:
  document jobs
  report sections
  findings
  legal/rule review records
  generated artifacts
  approval tasks
  delivery requests

UI displays and controls those same objects.
```

The user can start work from chat and finish in UI, or start from UI and ask a
worker to continue.

## First Practical Scope

Do not implement a broad worker platform first.

Start with a narrow ArchDox worker slice:

```text
1. DocumentQaWorker
   - input: report/document snapshot
   - output: findings
   - implementation: flower-ai-harness

2. ReviewPlannerWorker
   - input: findings + current report state + user command
   - output: one typed ArchDoxWorkerAction proposal
   - implementation: flower-ai-harness with strict schema validation

3. ArchDoxWorkerActionRegistry
   - fixed enum of allowed actions
   - no dynamic arbitrary tools

4. ArchDoxWorkerPolicyGate
   - simple allow/deny rules
   - loop limit
   - manual approval flag

5. ArchDoxWorkerExecutionFlow
   - Flower flow that executes one approved action and records trace
```

This is enough to prove the direction without building a generic framework.

## Report Task Chat Session Slice

The first user-facing slice is a project-selected, report-task chat entry
point. The project only provides the work boundary. The chat session itself is
temporary and follows one concrete report task.

Scope:

```text
Chat menu
  -> requires a selected project
  -> opens the current user's active worker chat session for that project
  -> guides the user through site selection and report selection
  -> persists messages on the server
  -> submits a controlled ArchDoxWorker action through Flower
```

Lifecycle:

```text
Project
  -> broad work boundary
  -> project deletion deletes worker chat sessions and messages

Site
  -> actual work target inside the project

Report
  -> center of the chat/worker task

Worker chat session
  -> temporary operation surface from report work through review/sign/generate
  -> keeps one active session per user/project in MVP
  -> stores selected siteId/reportId and current stage
  -> after document generation, messages should be deleted or reduced to an
     operation/audit summary
```

Why server persistence:

```text
- unfinished work should survive refresh/device changes
- worker actions must be auditable
- later approval/review flows need DB state, not browser localStorage
```

Retention must stay conservative. The chat is not a second hidden product and
not a generic infinite chatbot history. Completed document-generation sessions
should not keep full chat history by default. The durable record is the normal
ArchDox domain state: reports, steps, photos, findings, document jobs,
artifacts, and operation_events.

Initial action:

```text
WORKER_CHAT_ADVANCE
```

This action proves the controlled runtime path and the deterministic MVP chat
flow:

```text
message saved
-> assistant placeholder saved
-> ArchDoxWorkerExecutionFlow submitted
-> policy gate checks the action
-> registered executor advances the chat session
-> session stage decides the next prompt/options
-> operation_events records the trace
```

Current deterministic stages:

```text
AWAITING_SITE
  -> list project sites as choices
  -> selecting a site moves to AWAITING_REPORT
  -> if no site exists, CREATE_SITE can create one through the worker flow

AWAITING_REPORT
  -> list reports for the selected site
  -> selecting a report moves to REPORT_WORKING
  -> if no report exists, CREATE_REPORT can create one through the worker flow

REPORT_WORKING
  -> confirms the selected report
  -> exposes the selected report's workflow steps to the chat UI
  -> UPDATE_REPORT_STEP can save structured report step data through the worker flow
  -> SUBMIT_REPORT submits the selected report through the normal report validation path

REVIEWING
  -> report content is submitted and ready for document-tab preflight/review/generation
  -> RUN_PREFLIGHT_REVIEW starts the same preflight review run used by the document tab
  -> REQUEST_DOCUMENT_GENERATION creates the same document job used by the document tab
```

Real domain workers such as `DocumentQaWorker`, `InspectionLogWorker`, and
`ReviewPlannerWorker` should be connected after this path is stable.

The first domain-mutating worker actions are intentionally narrow:

```text
CREATE_SITE
  -> uses the normal SiteService
  -> inherits project structure permissions
  -> updates the chat session to the created site

CREATE_REPORT
  -> uses the normal InspectionReportService
  -> inherits report writer permissions and document type validation
  -> updates the chat session to the created report

UPDATE_REPORT_STEP
  -> uses the normal InspectionReportService.saveStep path
  -> validates the requested stepCode against the selected report workflow
  -> if no stepCode is supplied, selects the next unsaved workflow step
  -> stores data in inspection_report_steps
  -> keeps the existing report/document UI as the durable source of truth

SUBMIT_REPORT
  -> uses the normal InspectionReportService.submit path
  -> runs deterministic submit validation before changing status
  -> moves the report to READY_TO_GENERATE on success
  -> moves the chat session to REVIEWING
  -> exposes documentTabAvailable so the UI can guide the user to the document tab

RUN_PREFLIGHT_REVIEW
  -> uses the normal ReportPreflightReviewService.requestReview path
  -> starts deterministic validation and optional AI review through the existing Flower flow
  -> stores report_preflight_review_runs and findings exactly like the document tab
  -> keeps the report/document UI as the durable status view

REQUEST_DOCUMENT_GENERATION
  -> uses the normal DocumentJobService.create path
  -> enforces latest passed preflight review through DocumentPreflightGateService
  -> routes to the configured ArchDox Agent document worker
  -> creates document_jobs and submits the existing document-generation Flower flow
```

The chat layer does not write hidden product state. It proposes typed actions;
the ArchDox Worker Flower flow executes registered executors, and those
executors call the existing domain services.

Report writing through chat must remain schema-aware. The chat assistant may
help the user choose or fill a step, but it must not invent hidden step codes or
store arbitrary worker-only report content. The Cloud API resolves the report's
workflow definition and returns `workflowSteps`, `nextStepCode`, and the next
allowed action to the client. The client may present this as a simple selector,
but the server remains responsible for validating that the selected step belongs
to the report's configured workflow.

## Conversation Planner Harness V1

The first AI-assisted worker chat layer is `ConversationPlannerHarness` in
`archdox-ai-harness`.

Its job is deliberately narrow:

```text
user message + current project/site/report/session state + allowed actions
-> one action proposal
```

It does not execute the action. It returns a typed proposal such as:

```text
PROPOSE_ACTION / CREATE_SITE
PROPOSE_ACTION / CREATE_REPORT
PROPOSE_ACTION / UPDATE_REPORT_STEP
PROPOSE_ACTION / SUBMIT_REPORT
PROPOSE_ACTION / RUN_PREFLIGHT_REVIEW
PROPOSE_ACTION / REQUEST_DOCUMENT_GENERATION
ASK_CLARIFICATION
NO_ACTION
```

The Cloud API calls this harness only from the `WORKER_CHAT_ADVANCE` Flower
path, after the REST request has already returned a pending chat response. The
planner result is merged into assistant message metadata as `plannerProposal`
and, when allowed for the current stage, `nextAction` plus
`plannerSuggestedPayload`.

Execution remains controlled:

```text
ConversationPlannerHarness proposes
-> UI/user confirms or edits through normal worker chat controls
-> ArchDoxWorkerActionRegistry resolves the explicit action
-> ArchDoxWorkerPolicyGate validates
-> Flower executes
-> existing SiteService / InspectionReportService mutate domain state
```

This keeps the chatbot feeling natural without letting the AI directly call
tools or write database state.

## Planner Proposal Confirmation UI V1

The web chat UI renders the latest `plannerProposal` as a small confirmation
card. The card may show the proposed action, a short rationale, confidence, and
the safe payload summary that will be used for execution.

Only the latest completed assistant proposal is executable. Older proposals are
kept visible as conversation context but cannot be clicked again. This prevents
stale planner output from creating duplicate sites, duplicate reports, or
writing to an old report step.

When the user confirms a proposal, the client sends the same explicit worker
chat command used by the normal UI controls:

```text
CREATE_SITE         -> createSite
CREATE_REPORT       -> createReport
UPDATE_REPORT_STEP  -> updateReportStep
SUBMIT_REPORT       -> submitReport
RUN_PREFLIGHT_REVIEW -> runPreflightReview
REQUEST_DOCUMENT_GENERATION -> requestDocumentGeneration
```

The AI still does not execute anything directly. Confirmation only turns a typed
proposal into a normal ArchDox Worker action request, so registry, policy gate,
Flower execution, and existing domain services remain the single mutation path.

## Worker Chat UI Sync And Processing UX V1

Worker Chat is a second interface over the same project/site/report workflow,
not a separate product state. Whenever the chat session changes, the web app
must synchronize the normal selected context:

```text
chat session projectId -> selected project
chat session siteId    -> selected site
chat session reportId  -> selected report
```

If a Worker action creates or updates domain data, the normal workspace lists
must be refreshed so the user can leave chat and see the same site, report, or
saved report step in the standard UI.

The chat UI should also expose a lightweight processing state. While a pending
assistant message exists, the composer and action controls are disabled and a
small status bar shows the current work:

```text
요청을 보내는 중...
현장을 생성하는 중...
리포트를 생성하는 중...
리포트 내용을 저장하는 중...
다음 작업 흐름을 확인하는 중...
```

This is still REST plus polling, not token streaming. Streaming may be added
later through SSE or WebSocket if the interaction needs it, but the MVP contract
is durable chat messages plus session refresh.

## Worker Chat Review/Generation Status Sync V1

Worker Chat and the document tab must observe the same durable report/document
state. The chat session response now includes a `workflowState` snapshot for the
selected report:

```text
report
latestPreflightRun
latestDocumentJob
preflightActive
preflightPassedForCurrentRevision
documentJobActive
documentGenerated
canRunPreflightReview
canRequestDocumentGeneration
```

This snapshot is derived from the normal ArchDox tables:

```text
inspection_reports
report_preflight_review_runs
report_preflight_review_findings
document_jobs
```

It is not separate Worker state. If the user starts preflight review or document
generation from the document tab, Worker Chat sees it on the next session
refresh. If Worker Chat starts the work, the document tab sees the same run/job.

The web chat UI polls the session while either:

```text
assistant reply is pending
preflight review is REQUESTED/RUNNING
document job is REQUESTED/GENERATING
```

Document generation controls in chat are enabled only when the latest preflight
review has `PASSED` for the current report generation revision. If the review is
running, failed, stale, or needs attention, the chat shows the current status and
keeps generation disabled instead of relying on a backend error as the primary
user feedback.

## Worker Orchestration Growth Path

The current Worker implementation is intentionally small:

```text
one request
-> one ArchDoxWorkerExecutionFlow
-> one registered action
-> existing domain service mutation or assistant reply update
```

This is enough for the current Worker Chat slice because the connected actions
are short-lived:

```text
WORKER_CHAT_ADVANCE
CREATE_SITE
CREATE_REPORT
UPDATE_REPORT_STEP
SUBMIT_REPORT
RUN_PREFLIGHT_REVIEW
REQUEST_DOCUMENT_GENERATION
```

Do not hide long business workflows inside one action executor. When a Worker
task starts coordinating multiple user-visible steps, approvals, retries,
document generation, or several AI harness calls, introduce a higher-level
ArchDox Worker task flow instead of making the executor huge.

The intended future shape is:

```text
WorkerTaskOrchestrationFlow
-> plan current task
-> ask or wait for user input when needed
-> submit one or more ArchDoxWorkerAction requests
-> run deterministic validation first
-> call AI harness only for judgment/language/semantic gaps
-> branch/retry/stop according to policy
-> record operation events and final domain state
```

This higher-level flow should still use Flower for orchestration. Bloom may be
introduced later for event publication and module decoupling, but Bloom should
not replace the explicit Flower task flow that owns state transitions,
timeouts, retries, approvals, and recovery decisions.

For now:

```text
ArchDoxWorkerExecutionFlow = action execution envelope
ConversationPlannerHarness = AI proposal block
Worker Chat session       = temporary report-task conversation state
Existing domain services  = source of truth mutation path
```

Later:

```text
WorkerTaskOrchestrationFlow = multi-step business worker
ArchDoxWorkerExecutionFlow  = executes each allowed action
AI Harness flows            = bounded AI work blocks inside the task
Bloom events                = optional notifications between modules
```

The Worker must remain a controlled product feature. It is not a free-form AI
agent that directly manipulates tools or database state.

## What Belongs In ArchDox First

These should start inside ArchDox:

```text
ArchDoxWorkerRequest
ArchDoxWorkerAction enum
ArchDoxWorkerActionRegistry
ArchDoxWorkerPolicyGate
ArchDoxWorkerExecutionFlow
DocumentQaWorker
LegalReviewWorker
InspectionLogWorker
ReviewPlannerWorker
ArchDoxWorkerAuditEvent
Worker run DB tables
UI/chat integration
approval rules
office/project/report permission checks
```

They are ArchDox product features and should be shaped by real document
workflow needs.

## What Might Be Extracted Later

Only after ArchDox proves repeated, domain-neutral patterns should a separate
`flower-agent-orchestration` project be considered.

Possible extraction candidates:

```text
ActionRegistry
PlannerActionSchema
PolicyGate interface
LoopBudgetPolicy
ApprovalDecision model
AgentTrace model
ActionResult model
Planner execution pattern
```

Extraction rule:

```text
If the concept mentions reports, construction, supervision, offices,
documents, photos, approvals, or ArchDox permissions, it stays in ArchDox.

If the concept applies cleanly to any Java business workflow using Flower,
it may later move to flower-agent-orchestration.
```

## Non-Goals

Do not build these now:

- generic AI worker platform
- LangGraph clone
- autonomous agent that freely chooses arbitrary tools
- system where AI edits flow graphs or executes arbitrary code
- second product state hidden inside chat
- replacement for existing ArchDox UI
- replacement for ArchDox Agent
- replacement for flower-ai-harness
- full AI governance/compliance platform

## Product Positioning

ArchDox should be positioned as:

```text
Specialized AI workers for construction supervision and document workflows.
```

Not:

```text
Generic AI automation platform.
Generic chatbot.
Document template SaaS only.
```

The strategic product shift:

```text
Traditional SaaS:
  user operates software

ArchDox worker service:
  user delegates supervision/document work to controlled AI workers
  and remains in charge through UI review, approval, and audit
```

## Next Design Task

In the ArchDox implementation thread, use this document to design the first
worker-service slice:

```text
DocumentQaWorker
ReviewPlannerWorker
ArchDoxWorkerActionRegistry
ArchDoxWorkerPolicyGate
ArchDoxWorkerExecutionFlow
minimal ArchDoxWorkerAuditEvent
UI/chat entry point boundary
```

Keep the first version small. The goal is not to finish a worker platform. The
goal is to prove that ArchDox can provide domain-specific workers while keeping
AI actions controlled, traceable, and integrated with the existing document
workflow UI.

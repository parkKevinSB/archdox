# ArchDox Engine Boundary

## Decision

ArchDox may offer an external `ArchDox Engine API`, but the implementation
must start as an internal logical boundary inside `cloud-api`.

Do not create a separate process yet.

```text
ArchDox SaaS UI / SaaS REST
  -> ArchDox Engine Boundary
     -> legal corpus
     -> construction supervision domain data
     -> deterministic review
     -> AI harness
     -> worker/action policy
     -> document generation request

External Engine REST API / future MCP adapter
  -> ArchDox Engine Boundary
```

The important rule is that ArchDox SaaS and external Engine API must call the
same internal engine boundary. They must not have separate review logic,
separate legal rules, separate prompts, or separate document-generation
decision logic.

## Naming Rules

ArchDox already has several things that could be casually called "engine".
Use the following names consistently.

| Name | What It Is | What It Is Not |
| --- | --- | --- |
| `ArchDox SaaS` | Full hosted product with office, user, project, site, report, photo, document, admin, and ops UI. | Not a narrow document generator. |
| `ArchDox Engine API` | External product/API surface that exposes selected review, legal, checklist, and document workflow capabilities to outside systems. | Not the internal Java package name yet. Not a separate process by default. |
| `ArchDox Engine Boundary` | Internal application-service boundary used by SaaS and future external API. | Not a runtime engine like Flower. Not a renderer. |
| `document-engine` | Deterministic document rendering library for DOCX/HTML/PDF/HWPX-capable output adapters. | Not responsible for AI, legal review, permissions, workflow, or office data. |
| `ArchDox Agent` | Separate execution server for document rendering, file pickup, artifact upload, local/NAS/S3 storage access. | Not an AI agent. Not a free-form autonomous worker. |
| `ArchDox Worker Service` | Controlled action/policy/orchestration layer for user/system work such as chat-driven report updates. | Not a model provider. Not the document-rendering agent. |
| `archdox-ai-harness` | ArchDox-specific AI work blocks such as preflight review, ops diagnosis, and future legal review. | Not the whole business workflow. |
| `flower-ai-harness` | Generic AI harness runtime/framework used by ArchDox-specific harnesses. | Not ArchDox domain logic. |
| `Legal Domain` / `Legal Corpus` | Official-law source sync, versioning, diff, digest, and future legal-domain binding. | Do not call this `Legal Engine` unless it becomes an external product slice. |
| `Flower Engine` | Technical workflow runtime that executes flows/steps/workers. | Not ArchDox's business engine product. |

Avoid ambiguous names:

```text
AI Agent            -> use ArchDox AI Harness or model provider
Local Agent         -> use ArchDox Agent with deploymentMode=LOCAL_OFFICE
Legal Engine        -> use Legal Domain or Legal Corpus
Document Generator  -> use document workflow, document generation job, or document-engine renderer
Worker              -> use ArchDox Worker Service unless referring to Flower worker slots
```

## Product Boundaries

### ArchDox SaaS

ArchDox SaaS has the most context.

It knows:

```text
office
user and role
project
site
target
report and revision
photo evidence
supervision ledger entries
template/output layout
document job state
AI/feature policy
```

Therefore SaaS can build a rich engine input snapshot internally and call the
Engine Boundary without making the user provide every detail manually.

### ArchDox Engine API

The external Engine API should accept caller-provided context snapshots.

It can support customers who do not use ArchDox as the full SaaS platform but
want selected capabilities:

```text
preflight report review
source-backed legal risk review
checklist gap review
construction supervision checklist suggestion
document generation request, later and only if storage/agent policy is clear
legal change digest lookup
```

External API callers must not get direct DB access. They submit bounded input
and receive typed output.

### Future MCP Adapter

MCP is optional and should be an adapter over the Engine API or the same
Engine Boundary.

MCP must not become the core architecture. It is useful when an external AI
client wants to call ArchDox tools, but ArchDox still controls allowed actions,
policy, audit, and source-backed context.

External MCP callers must not be required or trusted to build ArchDox internal
DTOs. MCP should submit documents, raw facts, evidence, and answers into the
external context-normalization pipeline documented in
`ARCHDOX_MCP_GATEWAY_STRATEGY.md`; the Engine Boundary receives normalized
canonical context, not arbitrary customer data.

## Internal Engine Boundary Responsibilities

The Engine Boundary should own orchestration-facing application services, not
low-level rendering or raw source connectors.

It should coordinate:

| Capability | Boundary Role |
| --- | --- |
| Report preflight | Build review input from report snapshot or supplied context, run deterministic checks first, optionally submit AI harness. |
| Legal context | Resolve source-backed legal corpus snippets and domain bindings; never rely on AI memory. |
| Checklist/domain gap review | Compare construction-supervision domain catalog, ledger entries, and report payload. |
| Document readiness | Decide if a document can be generated for a specific report revision. |
| Document generation request | Submit or prepare the existing document-generation workflow; do not render inline in Cloud API. |
| Worker interaction | Translate worker/chat proposals into normal domain actions through registry/policy. |
| External API output | Return stable typed results, findings, references, and job ids. |

It must not:

```text
render DOCX/PDF directly
call official legal APIs on every user request
let AI call tools directly
skip office/user permission checks
store raw unbounded chat as source of truth
invent legal citations
duplicate SaaS and external API logic
```

## Engine And Worker Boundary

The Engine Boundary must not duplicate the ArchDox Worker governance layer.

The separation is:

```text
ArchDox Engine Boundary
  -> receives SaaS or external context
  -> normalizes context
  -> applies source-backed domain/legal recipes
  -> returns typed findings, references, missing-context questions, and
     suggested worker actions

ArchDox Worker Service
  -> owns action registry
  -> owns policy gate
  -> owns dry-run/approval/budget/state checks
  -> owns Flower-based action execution
  -> records trace/audit
  -> calls domain services for mutation
```

AI must not directly execute tools, mutate reports, call legal APIs, generate
documents, or publish final workflow state. AI harnesses and the Engine may
suggest or review. The Worker Service decides, through registered actions and
policy gates, what actually runs.

This is intentionally different from free-form tool calling:

| Layer | Responsibility |
| --- | --- |
| `Engine context intake` | Accept SaaS snapshots or external documents/facts and normalize them into public canonical context. |
| `Engine recipe validation` | Run source-backed catalog binding, legal-reference lookup, legal-risk context checks, and typed finding generation. |
| `Engine suggestedWorkerActions` | Optional recommendations that must name currently registered Worker actions, such as `RUN_PREFLIGHT_REVIEW`. Backlog ideas stay out of runtime metadata until executor/policy/tests exist. These are not executed by Engine. |
| `ArchDoxWorkerAction` | The controlled unit of work. |
| `ArchDoxWorkerActionRegistry` | Knows which actions exist and which executor/flow owns each action. |
| `ArchDoxWorkerPolicyGate` | Applies permissions, plan limits, office policy, approval rules, budget, and revision gates. |
| `ArchDoxWorkerExecutionFlowFactory` | Flower flow that records, resolves, gates, executes, and traces Worker actions. |
| Domain services | Perform actual business mutation through existing SaaS paths. |

Do not add these concepts under `cloud.engine`:

```text
EngineActionRegistry
EnginePolicyGate
EngineActionExecutionService
EngineTaskOrchestrationFlow
Engine-owned REQUEST_DOCUMENT_GENERATION execution
```

Those names recreate the Worker governance layer and make the architecture
split into two competing control planes. If Engine output needs a follow-up
action, it must be converted into an `ArchDoxWorkerAction` and pass the Worker
registry/policy/Flower path.

The first domain-backed validation slice is construction-supervision catalog
binding. When external context supplies `tradeCode`, `processCode`, and
`inspectionItemCode`, the Engine validates them against the same catalog used by
the SaaS daily-log workflow and returns either official catalog binding metadata
or typed findings such as `CATALOG_SELECTION_INVALID`.

Internal SaaS preflight now enters the same boundary for construction daily log
context. The SaaS report path keeps its existing deterministic validator for
required fields, revision state, submit readiness, and simple photo evidence
warnings, then builds a normalized Engine context from `DAILY_LOG.dailyItems`,
report id/revision, site/project ids, and photo evidence metadata.

```text
Document tab / Worker Chat preflight
  -> ReportPreflightReviewFlowService
  -> ReportPreflightDeterministicValidator
  -> ReportPreflightEngineBoundaryService
  -> EngineValidationService
  -> typed Engine findings merged into report_preflight_review_findings
```

This means SaaS and external Engine review-session validation now share the
same catalog/legal-risk recipe language. They still have different input
builders: SaaS builds context from durable ArchDox report state, while external
callers submit normalized or normalizable context through Engine review
sessions. Neither path executes Worker actions inside the Engine.

If active `legal_domain_bindings` exist for that catalog item, the same
validation response also returns typed `validationResult.legalReferences`.
These references are deterministic source links, not AI-generated legal
conclusions. They are the bridge from domain catalog item to legal corpus
material and should be used by future legal-risk review, AI harness prompts,
and human-readable findings. Metadata may keep a compatibility/debug copy, but
external clients should use the top-level field.

When Engine output includes `metadata.suggestedWorkerActions`, Cloud API must
interpret that list from the Worker side. The first implementation is
`EngineWorkerActionSuggestionBridge` under
`com.archdox.cloud.worker.engine`. It converts Engine suggestions into
`EngineWorkerActionCandidate` metadata for tracing and future execution
planning. It intentionally does not execute actions, call domain services, or
own policy decisions. Its package placement is deliberate: Engine may suggest,
Worker interprets and governs.

Engine findings may also include non-worker `nextActions` such as
`ADD_SUPERVISION_EVIDENCE_CONTEXT`. API/MCP responses expose these as typed
objects with `code`, `label`, `actionType`, `blocking`, and `targetTool`. Those
are user/product guidance, not runtime Worker actions. Do not convert them to
`ArchDoxWorkerAction` until a real executor, policy gate rule, and test
coverage exist.

The execution handoff is `EngineWorkerActionSubmissionService`, also under
`com.archdox.cloud.worker.engine`. It can submit runnable candidates into
`ArchDoxWorkerExecutionFlowFactory`, which means the real execution still runs
through Worker resolve, policy gate, Flower execution, and trace recording.
This service must skip candidates when:

```text
unknown action
disabled action
missing executor
workflow explicitly excludes that action, such as RUN_PREFLIGHT_REVIEW inside
  an already-running preflight flow
Worker Chat scoped executor is registered but no chat session payload exists
```

This last rule matters because several current MVP executors are still
Worker-Chat scoped. Engine follow-up should not accidentally call those
chat-specific executors without `sessionId` and `assistantMessageId`. Generic
executors can be introduced later without changing the Engine boundary.

The first legal-risk context check is deterministic recipe validation. When
legal references are attached but the normalized context lacks supervision
content, work area, photos, or evidence text, the Engine emits
`LEGAL_EVIDENCE_CONTEXT_MISSING` and adds
`metadata.legalRiskReview.aiPromptContext`. Future AI harnesses must consume
that source-backed prompt context instead of asking a model to remember or
invent legal references.

The separation is:

```text
Recipe:
  ArchDox-specific task flow such as report preflight, legal-risk review,
  checklist gap review, document readiness, and document generation request.
  Engine can review readiness and suggest follow-up actions, but it does not
  execute mutations.

Governance:
  typed action registry, policy gate, approval, quota, budget, trace, and audit.
  This stays in ArchDox Worker Service.
```

When the engine is extracted into a separate process, the same shape should
move with it:

```text
archdox-engine-service
  -> Engine Boundary controllers
  -> EnginePlannerHarness / legal-review harnesses
  -> context normalization
  -> recipe validation
  -> legal corpus read model
  -> returns typed findings and suggestedWorkerActions

cloud-api / archdox-worker
  -> converts approved suggestions to ArchDoxWorkerAction
  -> Worker registry / policy / Flower execution
  -> SaaS domain mutation
```

Until that extraction, `cloud-api` owns the boundary and must keep SaaS and
external Engine API behavior identical by routing both through the same
application services.

## Input Model Direction

The Engine Boundary should operate on snapshots. The source of the snapshot can
differ.

```text
SaaS mode:
  DB state -> snapshot builder -> Engine Boundary

External mode:
  request body -> validation/normalization -> Engine Boundary
```

Example conceptual request:

```json
{
  "mode": "SAAS_CONTEXT",
  "capabilities": [
    "PREFLIGHT_REVIEW",
    "LEGAL_RISK_REVIEW",
    "CHECKLIST_GAP_REVIEW"
  ],
  "effectiveDate": "2026-06-04",
  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
  "context": {
    "officeId": 1,
    "projectId": 10,
    "siteId": 20,
    "reportId": 30,
    "reportRevision": 2
  }
}
```

External mode should not use internal ids as its only contract:

```json
{
  "mode": "PROVIDED_CONTEXT",
  "capabilities": [
    "PREFLIGHT_REVIEW",
    "LEGAL_RISK_REVIEW"
  ],
  "effectiveDate": "2026-06-04",
  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
  "context": {
    "project": {},
    "site": {},
    "reportSnapshot": {},
    "ledgerEntries": [],
    "photos": []
  }
}
```

The result should be typed and stable:

```json
{
  "engineRunId": "eng_...",
  "status": "WARN",
  "generationAllowed": true,
  "findings": [
    {
      "code": "MISSING_PHOTO_EVIDENCE",
      "category": "EVIDENCE",
      "severity": "MEDIUM",
      "source": "DETERMINISTIC",
      "location": "dailyItems.groups[0].entries[1]",
      "message": "Photo evidence is recommended for this supervision item.",
      "legalReferenceIds": []
    }
  ],
  "legalReferences": [
    {
      "referenceId": "BUILDING_ACT:ARTICLE_025@2026-06-04",
      "actCode": "BUILDING_ACT",
      "actName": "Building Act",
      "articleNo": "25",
      "articleTitle": "Construction supervision",
      "sourceVersionKey": "2026-06-04",
      "effectiveDate": "2026-06-04",
      "relevance": "DIRECT"
    }
  ],
  "nextActions": [
    {
      "code": "ADD_SUPERVISION_EVIDENCE_CONTEXT",
      "label": "Add supervision evidence context",
      "actionType": "USER_INPUT",
      "blocking": true,
      "targetTool": ""
    }
  ]
}
```

## Deployment Direction

### Current

```text
cloud-api
  -> SaaS controllers
  -> future external Engine controllers
  -> Engine Boundary application services
  -> legal/report/document/worker packages
```

### Later, Only If Needed

```text
cloud-api
  -> archdox-engine-service
       -> legal corpus/read model
       -> review workers
       -> AI harness orchestration
       -> external Engine API
```

Extraction criteria:

- legal sync/index/review workloads slow down SaaS requests
- external Engine API traffic becomes significant
- external customers need separate quota/billing/isolation
- legal impact AI jobs become long-running and resource-heavy
- independent deployment of engine capabilities becomes operationally useful

## Development Plan

### Phase E-0: Naming And Boundary Documentation

Status: current phase.

Deliverables:

- this document
- documentation map update
- current state update

### Phase E-1: Internal Engine Boundary Skeleton

Add a small package inside `cloud-api`, for example:

```text
com.archdox.cloud.engine
  api
  application
  dto
```

Initial classes:

```text
ArchDoxEngineService
ArchDoxEngineRequest
ArchDoxEngineResponse
ArchDoxEngineCapability
ArchDoxEngineRunMode
ArchDoxEngineFinding
EngineValidationService
ArchDoxContextNormalizationService
```

The first implementation should prepare normalized context, run recipe
validation, and return typed findings/metadata. It must not define its own
action registry, policy gate, or Flower execution path.

### Phase E-2: SaaS Uses The Boundary

Refactor the existing report preflight/document readiness path so the SaaS
controller calls the Engine Boundary service rather than duplicating review
decision logic.

This phase should preserve existing REST behavior.

### Phase E-3: Worker-Controlled Engine Follow-Up

Connect Engine output to the existing ArchDox Worker governance path when a
real action is needed.

```text
EngineValidationResult.suggestedWorkerActions
  -> ArchDoxWorkerAction
  -> ArchDoxWorkerActionRegistry
  -> ArchDoxWorkerPolicyGate
  -> ArchDoxWorkerExecutionFlowFactory
  -> existing SaaS domain service
```

This is where `flower-agent-runtime` becomes concrete inside ArchDox. The
Engine is a proposer/reviewer. The Worker Service is the controlled execution
path.

### Phase E-4: External Engine REST V1

Add a narrow external API surface:

```text
POST /api/v1/engine/reviews/preflight
```

Start with `PROVIDED_CONTEXT` validation only, fake or deterministic output
allowed. Require API key or platform-issued token; do not expose to anonymous
users.

### Phase E-5: Legal Context Builder

Connect the Engine Boundary to legal corpus read models and construction
supervision domain bindings.

The legal API itself is not called here. The boundary reads already-synced DB
corpus.

### Phase E-6: Source-Backed Legal Review

Add a legal/compliance review harness that receives bounded source-backed
context from Phase E-4.

### Phase E-7: Optional MCP Adapter

Only after REST Engine API is stable, add MCP as an adapter for external AI
clients.

MCP tools should call the same Engine Boundary for review and context
normalization. If an MCP-triggered operation needs mutation, it must go through
ArchDox Worker policy/quota/audit. MCP must not bypass Worker governance.

## Summary

The correct direction is:

```text
one internal ArchDox Engine Boundary
used by SaaS first
then exposed through external REST
then optionally adapted through MCP
eventually extractable to archdox-engine-service
```

This keeps ArchDox from becoming a pile of unrelated engines while preserving
the option to sell selected capabilities as an external engine product later.


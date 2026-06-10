# ArchDox Engine Service Strategy

## Decision

`ArchDox Engine Service` is a distinct product/service direction from
`ArchDox SaaS`.

ArchDox SaaS is the full office platform. ArchDox Engine Service is the
review/legal/checklist/workflow engine that ArchDox SaaS uses internally and
that external customers may later call through REST or MCP adapters.

The key rule:

```text
one shared engine implementation
  -> used internally by ArchDox SaaS
  -> exposed externally through Engine API, later
  -> optionally adapted to MCP, later
```

Do not build a separate SaaS-only engine and a separate external engine. That
would create divergent legal rules, prompts, review behavior, and document
readiness decisions.

## Product Separation

| Product | Primary User | Scope |
| --- | --- | --- |
| `ArchDox SaaS` | Architecture offices and individual users using the full platform. | Project, site, report, photo, AI review, document generation, admin, ops. |
| `ArchDox Engine Service` | ArchDox SaaS internally, external SaaS vendors, custom AI agents, or customer systems. | Source-backed review, legal/domain context, checklist gap review, document workflow decisions. |
| `ArchDox Agent` | Office/cloud runtime controlled by ArchDox. | Rendering, local/NAS/S3 storage, artifact upload, file access. |

ArchDox Engine Service is not a replacement for ArchDox SaaS. It is the engine
behind selected SaaS features and a future external API product.

## Commercial Positioning

The external product must not be framed as a generic AI law-review wrapper.

Weak positioning:

```text
AI reads building law and reviews your document.
```

Strong positioning:

```text
ArchDox turns architecture and construction-supervision documents into a
reviewable business context, applies effective-date-specific law, standards,
domain rules, and office workflow rules, then returns structured findings,
evidence, correction actions, missing-context questions, and an audit trail.
```

This distinction matters because general AI agents can already summarize
documents, improve wording, search broad legal references, and find obvious
omissions. ArchDox's defensible value is:

```text
document-to-context normalization
effective-date and source-version-fixed review
document/work-type/domain rule engine
structured findings with basis and correction actions
review history, audit log, and customer mapping profile
```

The detailed business positioning is documented in
`ARCHDOX_ENGINE_BUSINESS_POSITIONING.md`.

## Consumption Modes

### Internal SaaS Consumption

ArchDox SaaS calls the Engine Boundary as a first-party internal caller.

Characteristics:

```text
caller = ArchDox SaaS
context = DB-backed office/project/site/report/photo snapshot
billing = not billable to ArchDox itself
quota = no commercial quota
guardrails = operational safety limits still apply
audit = still recorded
```

Internal use is effectively free and unlimited from a product billing
perspective. However, it must still be observable and protected against runaway
loops, repeated AI calls, stuck jobs, and accidental high-cost usage.

So the policy is:

```text
internal SaaS calls:
  no customer-facing engine fee
  no external API quota
  still subject to safety limits, AI provider budget, retry limits, and ops logs
```

### External Engine API Consumption

External callers submit bounded context and receive typed engine results.

Characteristics:

```text
caller = external customer/app/AI agent
context = provided request snapshot
billing = billable or plan-limited
quota = required
auth = API key, token, or platform-issued credential
audit = required
```

External callers must not receive raw DB access and must not bypass policy.
They call stable APIs such as:

```text
POST /api/v1/engine/reviews/preflight
POST /api/v1/engine/reviews/legal-risk
POST /api/v1/engine/checklists/gap-review
GET  /api/v1/engine/legal-updates
```

These endpoints should call the same internal Engine Boundary that SaaS uses.

Current first external slice:

```text
POST /api/v1/engine/external/review-sessions
GET  /api/v1/engine/external/review-sessions/{reviewSessionId}
POST /api/v1/engine/external/review-sessions/{reviewSessionId}/documents
POST /api/v1/engine/external/review-sessions/{reviewSessionId}/facts
POST /api/v1/engine/external/review-sessions/{reviewSessionId}/normalize
POST /api/v1/engine/external/review-sessions/{reviewSessionId}/run-validation
GET  /api/v1/engine/external/review-sessions/{reviewSessionId}/result
```

This is not the full commercial Engine API yet. It is the first authenticated
review-session boundary that future REST/MCP products can wrap.

External Engine API usage is now recorded at the call-event level for this
review-session surface. Successful external calls write to
`engine_api_usage_events` with API key id, owner user, optional office, engine
capability, operation, review session id, request units, metadata, and
timestamp. This is a usage/audit foundation only. It does not yet enforce
plan billing or assign customer billing plans.

The first quota foundation is API-key based. `engine_api_keys` stores
`daily_request_unit_limit`, and external review-session endpoints check that
limit before executing a request. Each successful external call currently costs
one request unit. This is intentionally simpler than final product billing:
there is no customer plan table, token-weighted pricing, or invoice aggregation
yet.

Platform admin usage read endpoints:

```text
GET /api/v1/platform-admin/engine/usage/events
GET /api/v1/platform-admin/engine/usage/summary
```

Supported filters include `apiKeyId`, `officeId`, `capability`, `operation`,
`reviewSessionId`, `from`, `to`, and `limit` where applicable. These endpoints
are for operational visibility and future billing preparation.

External authentication currently uses ArchDox Engine API keys:

```text
header: X-ArchDox-Engine-Key: adx_live_<keyId>_<secret>
or
header: Authorization: Bearer adx_live_<keyId>_<secret>
```

The raw key is shown only once at creation time. The database stores `keyId`,
metadata, scopes, status, and a SHA-256 hash of the secret material. No raw
secret is stored.

Platform admin key management endpoints:

```text
GET  /api/v1/platform-admin/engine/api-keys
POST /api/v1/platform-admin/engine/api-keys
POST /api/v1/platform-admin/engine/api-keys/{apiKeyId}/revoke
```

Logged-in user connect bootstrap endpoints:

```text
GET  /api/v1/engine/connect/clients
POST /api/v1/engine/connect/bootstrap
```

These are the first "connect my AI" foundation. They do not implement a live
MCP protocol server yet. They call `EngineConnectBootstrapWorker`, which
verifies the current user's optional office membership, issues a scoped Engine
API key, and returns a one-time setup package for Codex, Claude, Cursor,
ChatGPT, or a custom agent.

Self-service connect bootstrap keys must have a bounded lifetime. The current
MVP default is 90 days, with a maximum of 365 days. Platform-admin issued keys
may use a different explicit policy, but user-driven connect bootstrap must not
silently create indefinite external-agent credentials.

This worker is deterministic and synchronous for now. It should not be turned
into a long-running Flower flow until the connection process needs approval
callbacks, OAuth token exchange, remote client provisioning, setup status
polling, or retry/recovery.

Supported first scopes:

```text
ENGINE_REVIEW_SESSION
LEGAL_UPDATES
LEGAL_SEARCH
ALL
```

External Engine API calls are resolved to the API key's `ownerUserId`, with
optional `officeId` recorded on the review session. This keeps the first V1
boundary simple while leaving room for dedicated external customer/tenant
identity later.

External review-session access must preserve the full Engine API principal. Do
not collapse external requests into only `UserPrincipal(ownerUserId)`. Reads and
mutations for external review sessions are isolated by:

```text
externalSessionId + ownerUserId + officeId
```

If an Engine API key has no office binding, it may access only sessions created
with no office binding. This prevents keys issued for one office context from
reading review sessions created under another office for the same user.

### Current Validation Execution Path

The first review-session validation path must use the Engine Boundary for
context and recipe validation, but it must not create a second governance stack.

Current actual path:

```text
POST /api/v1/engine/external/review-sessions/{id}/run-validation
  -> EngineReviewSessionService
  -> normalize context if needed
  -> EngineValidationService
  -> context/catalog/legal recipe validation
  -> typed EngineValidationResult
  -> persisted validation_result_json
```

Internal SaaS preflight has also started using the same Engine Boundary.

```text
Document tab or Worker Chat preflight request
  -> existing report preflight Flower flow
  -> ReportPreflightReviewFlowService
  -> existing deterministic submit/report validator
  -> ReportPreflightEngineBoundaryService
  -> EngineValidationService
  -> report_preflight_review_findings
```

The SaaS path is not calling the external REST endpoint and it is not creating
an Engine API key. It calls the internal boundary directly because SaaS already
has trusted DB-backed context. The important part is that catalog binding,
legal reference attachment, legal-risk context checks, and typed findings are
shared with the external Engine path.

The Engine must not own an action registry, policy gate, executor, or Flower
task flow. Those belong to ArchDox Worker Service. If Engine validation decides
that a follow-up controlled operation is useful, it may return
`suggestedWorkerActions`, but only for actions that currently exist in
`ArchDoxWorkerActionRegistry` with an executor/policy/test path. Example:

```text
RUN_PREFLIGHT_REVIEW
REQUEST_DOCUMENT_GENERATION
```

Those suggestions are advisory. They must be converted to
`ArchDoxWorkerAction` and pass `ArchDoxWorkerActionRegistry`,
`ArchDoxWorkerPolicyGate`, and `ArchDoxWorkerExecutionFlowFactory` before
anything is executed or mutated.

Validation findings should be generated through typed
`ArchDoxEngineFinding`/`EngineValidationResult` objects. The database may still
store the result as JSON, but external response construction must not be driven
by ad hoc `Map<String,Object>` finding assembly.

The current V1 validation also performs construction-supervision catalog
binding when the caller provides `tradeCode`, `processCode`, and
`inspectionItemCode` context facts. The binding uses
`SupervisionDomainCatalogService`, returns official catalog bindings in result
metadata, and emits typed findings for incomplete or invalid catalog selections.

This gives external Engine/API/MCP callers the same domain vocabulary boundary
as ArchDox SaaS without exposing database tables or trusting caller-provided
display names.

Example external facts:

```json
{
  "facts": [
    {"name": "tradeCode", "rawValue": "REINFORCED_CONCRETE"},
    {"name": "processCode", "rawValue": "REBAR_ASSEMBLY"},
    {"name": "inspectionItemCode", "rawValue": "RC_REBAR_COUNT_DIAMETER_PITCH"}
  ]
}
```

Possible catalog findings:

```text
CATALOG_SELECTION_INCOMPLETE
CATALOG_SELECTION_INVALID
```

This V1 is source-backed by ArchDox's versioned domain catalog.

The next source-backed slice also exists at foundation level:
`EngineLegalReferenceBindingService` resolves typed `legalReferences` in the
validation result. It prefers active `legal_domain_bindings` for matched catalog
bindings. If no binding is available for the construction-supervision context,
it falls back to synchronized legal corpus candidates from tracked acts such as
`BUILDING_ACT` and `CONSTRUCTION_SUPERVISION_DETAILED_STANDARD`.
This does not yet decide legal compliance. It gives the Engine a deterministic
way to attach source references such as act code, article number, source
version key, effective date, source URL, relevance, catalog code, and checklist
item code to the review context before AI or a human reviewer reasons over it.

The external response contract now exposes those references as typed
top-level `validationResult.legalReferences`. Metadata may keep a compatibility
copy for debugging, but clients should treat the top-level field as the stable
contract.

`EngineLegalRiskContextReviewService` then uses those source references as
guardrails for the first legal-risk context check. If legal/domain references
exist but the caller did not provide supervision narrative, work area, photo, or
other evidence context, the Engine returns:

```text
LEGAL_EVIDENCE_CONTEXT_MISSING
```

The same result metadata includes `legalRiskReview.aiPromptContext`. This
prompt context keeps compact legal reference metadata such as `sourceCode`,
`sourceUrl`, `articleVersionId`, `sourceVersionKey`, and `effectiveDate` so a
future legal-review harness can cite supplied sources only instead of inventing
law references.
prompt context is not sent to a model yet. It is the safe package future AI
harness steps should use: domain catalog bindings, legal references, supplied
evidence context, and strict instructions not to invent legal citations.

The context check is Engine recipe validation, not a controlled Engine action.
If the result should lead to actual work before a Worker action exists, the
Engine should return non-worker `nextActions` such as
`ADD_SUPERVISION_EVIDENCE_CONTEXT`. In API/MCP responses these are typed
objects with `code`, `label`, `actionType`, `blocking`, and `targetTool`
fields, not bare strings. Future ideas such as legal review or
evidence-gap analysis are tracked in the Worker action backlog and must be
added only when executor, policy, and tests are implemented together. The next
step is to expand legal-domain bindings beyond test/admin seed data and let a
dedicated legal-review harness consume `aiPromptContext` through Worker
governance when execution is required.

### Future MCP Adapter

MCP is an adapter for AI clients, not the core service. Detailed MCP gateway,
agent-connect bootstrap, review-session, and context-normalization rules live
in `ARCHDOX_MCP_GATEWAY_STRATEGY.md`.

```text
Claude/Cursor/customer AI
  -> MCP adapter
  -> Engine API or Engine Boundary
  -> recipe validation and suggestedWorkerActions
  -> ArchDox Worker policy/Flower path, only when mutation or controlled work is required
```

MCP must not allow an external model to directly mutate ArchDox data, call
legal APIs, generate documents, or access raw office data. MCP exposes Engine
review/context tools first. Controlled work must go through ArchDox Worker.

External MCP callers should not be asked to produce ArchDox internal DTOs.
They provide documents, raw customer data, extracted facts, evidence, confidence
scores, and user answers. ArchDox normalizes that input into a public canonical
schema and then translates it into internal rule/review context.

## Relationship To Flower Agent Orchestration

ArchDox Engine Service should integrate with the `flower-agent-runtime` concept
by acting as a proposer/reviewer, not by re-implementing the worker runtime:

```text
Engine/context/harness produces findings and suggestedWorkerActions
  -> Cloud API converts an approved suggestion to ArchDoxWorkerAction
  -> ArchDoxWorkerActionRegistry verifies the action
  -> ArchDoxWorkerPolicyGate checks permission, quota, approval, budget, and state
  -> ArchDoxWorkerExecutionFlowFactory runs the Flower flow
  -> domain services mutate durable state, if mutation is allowed
  -> Worker trace/audit/result is recorded
```

This makes the Engine Service suitable for both internal and external use.

Examples:

| Suggested Worker Action | Internal SaaS Policy | External Engine Policy |
| --- | --- | --- |
| `RUN_PREFLIGHT_REVIEW` | Allowed if user can access report. | Allowed if request is authenticated and within quota. |
| `REQUEST_DOCUMENT_GENERATION` | Allowed only through existing document job/Agent flow. | Usually denied at first; may require contract/storage policy later. |

Future legal review, evidence gap, human review, approval, and submission
package actions remain backlog items until they become real Worker actions.

The AI harness is never the authority. It can suggest findings or next actions.
The Worker policy and Flower execution path decide what happens.

## Data And Billing Boundaries

Internal SaaS and external Engine API can share implementation, but their
commercial and security boundaries differ.

| Concern | Internal SaaS | External Engine API |
| --- | --- | --- |
| Office DB access | Direct through SaaS services. | No direct DB access. |
| Input context | Built from ArchDox DB. | Supplied as request snapshot. |
| Billing | Not billed as Engine usage. | Metered by request, token, job, or plan. |
| Quota | Operational safety only. | Contract/plan quota required. |
| Audit | Required. | Required. |
| AI cost tracking | Required for ops. | Required for billing and ops. |
| Legal references | Source-backed only. | Source-backed only. |

Even internal calls should write enough telemetry to answer:

```text
which engine capability ran
which office/report/request it ran for
which provider/model was used
how many tokens/cost were consumed
which findings were produced
which worker actions were suggested/executed
```

External Engine output should be shaped around a durable review record, not only
an immediate answer.

Minimum external review metadata:

```text
review id
tenant/customer id
requested by
input hash
document version
effective date
law/source version
rule set version
engine version
finding codes
missing-context questions
created at
```

This metadata supports repeatability, audit, user correction, cost attribution,
and future billing. The Engine may expose this through REST or MCP, but both
channels must use the same review record semantics.

## Extraction Direction

### Current Shape

```text
cloud-api
  -> SaaS controllers
  -> internal Engine Boundary
  -> legal/report/document/worker services
  -> Flower flows
```

### Future Shape

```text
cloud-api
  -> ArchDox SaaS REST/UI
  -> calls archdox-engine-service for engine capabilities

archdox-engine-service
  -> Engine REST API
  -> optional MCP adapter
  -> context normalization
  -> Engine recipe validation
  -> EnginePlannerHarness, only for suggestions
  -> legal/domain read models

cloud-api / archdox-worker
  -> Worker action registry
  -> Worker policy gate
  -> Worker Flower execution
  -> SaaS domain mutation
```

Extraction should happen only when there is a real operational reason:

```text
external Engine traffic grows
legal sync/index/review becomes resource-heavy
engine customers need separate quota/billing/isolation
MCP/API product becomes independently useful
engine deployment cadence differs from SaaS
```

Until then, keep the code physically inside `cloud-api` but logically separated
under the Engine Boundary.

This is not an invitation to let `cloud-api` become an unstructured catch-all.
Engine-related code should stay behind explicit packages and stable service
interfaces. The first target package boundary is:

```text
com.archdox.cloud.engine.api
com.archdox.cloud.engine.application
com.archdox.cloud.engine.context
com.archdox.cloud.engine.domain
com.archdox.cloud.engine.infra
```

If these packages begin to need independent scaling, external customer
isolation, or a different deployment cadence, move them into
`archdox-engine-service`.

## Implementation Plan

### Phase ES-1: Service Strategy Documentation

Status: current.

Deliverables:

```text
ARCHDOX_ENGINE_SERVICE_STRATEGY.md
README documentation map update
CURRENT_STATE update
```

### Phase ES-2: Internal SaaS Uses Engine Boundary

Move existing SaaS review/readiness paths behind the Engine Boundary:

```text
report preflight
legal risk review, when source-backed context exists
checklist/domain gap review
document readiness decision
```

No external endpoint yet.

Status:

```text
started:
  report preflight now invokes ReportPreflightEngineBoundaryService
  construction daily log DAILY_LOG selections are validated through Engine recipes
  Engine findings are merged into the existing report preflight finding table

remaining:
  document readiness decision should be expressed through the same boundary
  checklist/domain gap review needs a dedicated recipe
  legal-review harness should consume source-backed legalRiskReview.aiPromptContext
  any mutation should be converted to ArchDoxWorkerAction and executed by Worker
```

### Phase ES-3: Worker-Controlled Follow-Up

Connect Engine findings/suggestions to the existing Worker control plane:

```text
EngineValidationResult.suggestedWorkerActions
  -> ArchDoxWorkerAction
  -> ArchDoxWorkerActionRegistry
  -> ArchDoxWorkerPolicyGate
  -> ArchDoxWorkerExecutionFlowFactory
  -> existing domain service
```

This phase should not introduce `EngineActionRegistry` or `EnginePolicyGate`.
Do not let AI or Engine recipes call domain mutation services directly.

Status:

```text
foundation implemented:
  EngineWorkerActionSuggestionBridge lives under cloud.worker.engine
  EngineValidationResult.metadata.suggestedWorkerActions can be converted into
  EngineWorkerActionCandidate objects
  EngineWorkerActionSubmissionService can submit runnable candidates into
  ArchDoxWorkerExecutionFlowFactory
  report preflight operation-event metadata records candidate/submission status
  report preflight excludes RUN_PREFLIGHT_REVIEW to prevent recursive self-submit
  RUN_PREFLIGHT_REVIEW and REQUEST_DOCUMENT_GENERATION have generic Worker
  executors that can run without Worker Chat payload

remaining:
  approval/dry-run UX for suggested follow-up actions is not implemented
  Engine must remain a recipe/context boundary, not an action executor
```

### Phase ES-4: External Engine API V1

Expose one narrow external endpoint first:

```text
POST /api/v1/engine/reviews/preflight
```

Start with `PROVIDED_CONTEXT` only. Require authentication and quota. Return
typed findings and no mutation.

### Phase ES-5: External Legal/Checklist Capabilities

Add source-backed legal and checklist capabilities after legal corpus/domain
catalogs are stable:

```text
POST /api/v1/engine/reviews/legal-risk
POST /api/v1/engine/checklists/gap-review
GET  /api/v1/engine/legal-updates
```

### Phase ES-6: Billing, Quota, And Developer Keys

Add external customer identity, API keys, usage records, quota, and billing
read models.

Internal SaaS calls remain non-billable but still visible in ops usage.

Status:

```text
partial foundation implemented:
  API key table
  platform-admin create/list/revoke
  external review-session authentication
  scope gate for ENGINE_REVIEW_SESSION
  engine_api_usage_events call-event table
  external review-session endpoint usage recording
  platform-admin usage events/summary read API
  API-key daily request-unit limit
  external review-session quota guard

not implemented yet:
  customer billing tenant
  plan quota
  customer/plan-level quota enforcement
  billing aggregation
  developer portal UI
```

### Phase ES-7: Optional MCP Adapter

Add MCP only after REST Engine API and context-normalization sessions are
stable and policy-controlled.

The MCP adapter should wrap the same review-session services:

```text
create_review_session
submit_document
submit_context_facts
normalize_context
get_missing_context_questions
submit_context_answers
run_validation
get_review_result
```

## Non-Negotiable Rules

```text
SaaS and external Engine API must share the same engine implementation.
External callers must not access ArchDox DB directly.
AI must not directly execute tools or mutate state.
Legal review must be source-backed.
Internal SaaS use is commercially free, but not invisible.
External Engine use must be authenticated, metered, and quota-controlled.
Document rendering remains an Agent/document-engine responsibility.
```

## Short Summary

ArchDox SaaS is the full platform.

ArchDox Engine Service is the reusable review/legal/checklist/workflow engine.

The SaaS uses it internally without commercial quota. External customers use
the same implementation through controlled API/MCP surfaces with authentication,
quota, usage tracking, and policy gates.


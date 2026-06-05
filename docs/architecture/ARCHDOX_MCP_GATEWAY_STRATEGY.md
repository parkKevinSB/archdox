# ArchDox MCP Gateway Strategy

## Decision

ArchDox should provide MCP eventually, but MCP must be treated as an external
adapter over `ArchDox Engine Service`, not as the core engine itself.

The MCP product direction is:

```text
Customer Agent / Codex / Claude / ChatGPT / Custom AI
  -> ArchDox Agent Connect Bootstrap
  -> customer approval and OAuth
  -> ArchDox MCP Gateway
  -> Context Intake / Normalization
  -> ArchDox Engine Boundary
  -> legal, checklist, review, workflow result
```

MCP lets external AI agents use ArchDox capabilities. It must not expose raw
database access, internal DTOs, provider API keys, master tokens, or unrestricted
workflow mutation.

MCP is a distribution channel, not the commercial product core. The commercial
product core is the ArchDox Review Engine:

```text
Document Intake
-> Context Normalization
-> Public Canonical Context
-> Rule Engine
-> Source-backed Review Result
-> Correction Actions
-> Audit Log
```

MCP should make that engine callable from customer agents such as Codex, Claude,
Cursor, ChatGPT, or a custom agent. It must not become a shortcut that lets an
external model invent context, bypass rule selection, skip quota, or mutate
ArchDox state directly.

## Why This Is Separate From ArchDox SaaS

ArchDox SaaS already owns its office/project/site/report/photo data. It can
build a trusted internal snapshot directly.

External MCP callers do not have ArchDox-native data. They may have documents,
customer-specific structured data, extracted facts, or partial answers. That
input must be normalized before it reaches the Engine Boundary.

So the split is:

```text
Internal SaaS:
  ArchDox DB snapshot
  -> SaaS snapshot builder
  -> Canonical Engine Context
  -> Engine Boundary

External MCP / Engine API:
  customer document/facts/structured data
  -> Context Intake Layer
  -> Context Normalization Layer
  -> Public Canonical Schema
  -> Domain Context Translator
  -> Engine Boundary
```

Internal SaaS does not need customer field mapping or external review-session
intake. But it should still converge on the same canonical engine context so
SaaS, REST Engine API, and MCP produce consistent results.

## Agent Connect Bootstrap

External users should not have to manually edit MCP configuration, copy scopes,
or guess endpoint/auth settings.

ArchDox should offer a product-facing bootstrap/provisioning API:

```text
Agent Host
  -> POST /api/v1/agent-connect/bootstrap
  -> ArchDox returns MCP endpoint, auth metadata, scopes, tool packs, and approval URL
  -> customer approves
  -> OAuth token is issued
  -> Agent Host calls ArchDox MCP tools/list
```

Bootstrap returns a connection proposal, not credentials with unrestricted
access.

### Current MVP Connect Bootstrap

The first implemented slice is intentionally smaller than full OAuth/MCP
provisioning.

Implemented authenticated SaaS endpoint:

```text
GET  /api/v1/engine/connect/clients
POST /api/v1/engine/connect/bootstrap
```

This endpoint is called by a logged-in ArchDox user. The internal
`EngineConnectBootstrapWorker`:

```text
validate requested client type
-> verify optional office membership
-> issue a scoped Engine API key for the current user
-> compose Engine API/MCP endpoint metadata
-> return suggested MCP config and REST curl example
```

The current key scope is:

```text
ENGINE_REVIEW_SESSION
LEGAL_UPDATES
LEGAL_SEARCH
```

The raw key is returned only once. It is intended for early Codex/Cursor/Claude
or custom-agent testing against the external Engine API review-session and
source-backed legal read surfaces.
Self-service bootstrap keys have a bounded lifetime by default. The MVP default
is 90 days and the maximum is 365 days unless a future plan/admin policy says
otherwise.

This worker does not yet remotely configure the customer's AI client. It gives
the client or UI enough information to guide setup safely. When one-click
install, OAuth approval, callback handling, or connection status polling is
added, this worker should be wrapped by a Flower-controlled connection flow.

The current external review-session validation path already preserves the
engine governance boundary:

```text
review-session run-validation
  -> context normalization, if needed
  -> EngineValidationService
  -> catalog/legal/context recipe validation
  -> typed findings/result
```

If the validation result suggests controlled follow-up work, that suggestion
must be converted to an `ArchDoxWorkerAction` and pass Worker registry, policy,
Flower execution, and audit. MCP tools must wrap the same path rather than
introducing a second direct validation or mutation path.

The implemented response can include:

```text
connection id
client type
one-time Engine API key
MCP server URL placeholder
Engine API base URL
suggested MCP config
REST example
next setup steps
```

It must still not return:

```text
raw DB access
provider API keys
unrestricted master credentials
office data snapshots
internal rule database
```

Allowed bootstrap output:

```text
connection id
approval status
MCP endpoint
transport type
OAuth authorization URL
required scopes
available tool packs
human-readable instructions
```

Forbidden bootstrap output:

```text
master API key
long-lived unrestricted access token
raw DB connection
provider API keys
all customer data access
internal rule database
```

Example conceptual response:

```json
{
  "service": "ArchDox Agent Connect",
  "connectionId": "conn_001",
  "status": "approval_required",
  "mcp": {
    "serverUrl": "https://mcp.archdox.com/mcp",
    "transport": "streamable_http",
    "protocol": "mcp"
  },
  "auth": {
    "type": "oauth2",
    "authorizationUrl": "https://archdox.com/connect/approve/conn_001",
    "requiredScopes": [
      "law:read",
      "review:create",
      "review:read"
    ]
  },
  "toolPacks": [
    {
      "name": "building_law_review",
      "tools": [
        "search_law_article",
        "validate_inspection_report",
        "get_review_result"
      ]
    }
  ]
}
```

## OAuth And Approval Policy

Protected MCP access must go through customer approval and OAuth-style token
issuance.

The security model is:

```text
unauthenticated MCP request
  -> 401 with auth metadata
  -> client discovers authorization server
  -> customer login/approval
  -> short-lived access token
  -> tools/list returns only allowed tools
  -> tools/call is scope/policy checked
```

The bootstrap/provisioning worker may compute a connection proposal, scopes,
tool packs, and approval URL. It must not mint unlimited authority.

## Tool Pack Model

Tool availability should depend on tenant, plan, scope, and policy.

Example product tiers:

| Plan | Example Tool Pack |
| --- | --- |
| Starter | `search_law_article`, `validate_inspection_report_basic` |
| Professional | `validate_inspection_report`, `generate_compliance_checklist`, `get_review_result` |
| Enterprise | `custom_rule_review`, `project_document_audit`, `private_rule_set_validation` |

The same MCP Gateway can return different `tools/list` results per customer.

## Context Normalization Pipeline

External agents must not be responsible for producing ArchDox internal DTOs.

Bad direction:

```text
Customer Agent
  -> directly creates ArchDox internal ProjectContext/RuleEvaluationContext
  -> calls validation
```

This is unsafe because customer terminology, schema, and value meaning differ.

Better direction:

```text
Customer Agent
  -> submits document, raw customer data, extracted facts, evidence, confidence
  -> ArchDox normalizes and validates
  -> ArchDox builds canonical context
  -> ArchDox runs rules/legal review
```

The important layers:

```text
Context Intake Layer
  receives documents, extracted facts, customer structured data, user answers

Context Normalization Layer
  maps customer terms, normalizes enums, converts units, detects ambiguity,
  calculates missing fields

Public Canonical Schema
  stable external-facing context shape, separate from internal entities

Domain Context Translator
  converts public canonical context into internal domain/rule context
```

## Review Session Model

MCP should not be a single `validate_document(document)` call only. External
context is often incomplete, so a session model is better.

Recommended MCP tools:

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

This lets the external agent do what it is good at:

```text
read user documents
query customer systems
ask the user follow-up questions
submit raw facts and evidence
explain the result
```

And lets ArchDox do what it must own:

```text
normalize customer values
decide ambiguity
calculate missing context
select rule set
perform legal/domain review
produce findings with evidence and limitations
```

## Public Canonical Schema

ArchDox internal entities must not be exposed directly to MCP clients.

Use a stable public schema:

```json
{
  "building": {
    "uses": [
      "NEIGHBORHOOD_LIVING_FACILITY"
    ],
    "structureType": "REINFORCED_CONCRETE",
    "floor": {
      "basement": 1,
      "ground": 5
    },
    "totalAreaSquareMeter": 1200
  },
  "construction": {
    "phase": "STRUCTURAL_WORK",
    "workType": "FOUNDATION_CONCRETE_PLACEMENT"
  },
  "document": {
    "type": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
    "workDate": "2026-06-04"
  }
}
```

This schema may resemble internal data, but it is not the internal entity
model. Internal changes should not break external clients.

## Customer Mapping Profile

For enterprise customers with ERP/DMS/PMS data, add a customer mapping profile:

```text
Customer ERP/DMS schema
  -> Customer Mapping Profile
  -> ArchDox Public Canonical Schema
  -> Domain Context Translator
  -> Engine Boundary
```

Example:

```json
{
  "customerFieldMappings": [
    {
      "customerField": "projectType",
      "archdoxField": "building.uses",
      "valueMap": {
        "근생": "NEIGHBORHOOD_LIVING_FACILITY",
        "업무": "BUSINESS_FACILITY"
      }
    },
    {
      "customerField": "constMethod",
      "archdoxField": "building.structureType",
      "valueMap": {
        "RC": "REINFORCED_CONCRETE",
        "S": "STEEL"
      }
    }
  ]
}
```

This mapping should be managed by ArchDox onboarding/admin workflows, not
invented ad hoc by an external LLM at call time.

## External Review Modes

MCP and external Engine API can support three product modes.

| Mode | Description | Fit |
| --- | --- | --- |
| Document-only Review | Customer sends only document text/file. ArchDox extracts and asks follow-up questions. | Starter, easy onboarding, lower precision. |
| Agent-assisted Context Review | Customer agent sends document plus extracted facts and user answers. | Professional, better precision. |
| Integrated Context Review | Customer system uses mapping profile and public canonical schema. | Enterprise, repeatable high precision. |

## Component Plan

Initial components should stay inside `cloud-api` until the Engine Service is
worth extracting.

```text
AgentConnectController
AgentConnectionProvisioningService
ToolPackRegistry
ScopePolicyService
TenantEntitlementService
ConnectionSessionRepository

ArchDoxMcpGateway
McpToolRouter
ContextIntakeService
ContextNormalizationService
DomainContextTranslator
ExternalInspectionReviewService
UsageMeteringService
AuditLogService
```

When extracted:

```text
archdox-engine-service
  -> Agent Connect Bootstrap API
  -> MCP Gateway
  -> Context Intake / Normalization
  -> Engine Boundary
  -> usage and audit read models
```

## Relationship To Internal SaaS

Internal ArchDox SaaS does not need MCP to call the engine.

It should call the Engine Boundary directly:

```text
ArchDox SaaS controller/service
  -> SaaS snapshot builder
  -> Canonical Engine Context
  -> Engine Boundary
```

There is no need to run internal SaaS requests through external MCP bootstrap,
OAuth tool discovery, or customer mapping profiles.

However, both internal and external paths should eventually converge on the
same canonical engine context and the same rule/review implementation.

## Development Plan

### Phase MCP-1: Strategy And Schema Documentation

Status: current.

Document Agent Connect Bootstrap, OAuth/tool-pack policy, review sessions,
public canonical schema, and context normalization pipeline.

### Phase MCP-2: Public Context Skeleton

Add internal Java types for public canonical context, context facts, missing
questions, ambiguity candidates, and review session ids.

No public endpoint yet.

Initial skeleton exists under:

```text
cloud-api/src/main/java/com/archdox/cloud/engine/context
```

It currently provides:

```text
ArchDoxContextFact
ArchDoxContextFactSource
ArchDoxCanonicalContextValue
ArchDoxContextAmbiguity
ArchDoxMissingContextQuestion
ArchDoxNormalizedContext
ArchDoxContextNormalizationService
```

This is only a deterministic skeleton. It does not expose MCP, persist review
sessions, or handle customer-specific mapping profiles yet.

### Phase MCP-3: Context Intake And Normalization Skeleton

Add service interfaces and a deterministic fake normalizer for local tests.

Initial implementation now includes a durable REST-backed review-session
skeleton:

```text
POST /api/v1/engine/review-sessions
GET  /api/v1/engine/review-sessions/{id}
POST /api/v1/engine/review-sessions/{id}/documents
POST /api/v1/engine/review-sessions/{id}/facts
POST /api/v1/engine/review-sessions/{id}/normalize
POST /api/v1/engine/review-sessions/{id}/run-validation
GET  /api/v1/engine/review-sessions/{id}/result
```

Storage:

```text
engine_review_sessions
```

This is still not public MCP. It is the service surface MCP tools will wrap
later. It is intentionally authenticated through the normal Cloud API security
path for now; production external access still needs API key/OAuth/scope/quota.

### Phase MCP-4: External Engine REST Session V1

Harden the REST session endpoints before MCP:

```text
POST /api/v1/engine/external/review-sessions
GET  /api/v1/engine/external/review-sessions/{id}
POST /api/v1/engine/external/review-sessions/{id}/documents
POST /api/v1/engine/external/review-sessions/{id}/facts
POST /api/v1/engine/external/review-sessions/{id}/normalize
POST /api/v1/engine/external/review-sessions/{id}/run-validation
GET  /api/v1/engine/external/review-sessions/{id}/result
```

This lets ArchDox test the same engine flow without MCP protocol complexity.
The next work in this phase is to keep hardening external API authentication,
tenant/plan binding, quota, and usage metering. The first call-event usage
table now exists as `engine_api_usage_events`, and successful external
review-session REST calls record usage events. Platform admins can query those
events and summaries through `/api/v1/platform-admin/engine/usage/events` and
`/api/v1/platform-admin/engine/usage/summary`. External review-session calls
also pass an API-key daily request-unit quota guard before execution. Customer
plan quota, billing aggregation, and invoice-grade metering are still future
work. Do not add an `EngineTaskOrchestrationFlow` behind `run-validation`;
controlled execution belongs to ArchDox Worker, not Engine.

### Phase MCP-5: MCP Gateway V1

Wrap the REST/session services with MCP tools:

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

Current MVP implementation has started inside `cloud-api`:

```text
POST /api/v1/mcp
```

This endpoint is a Streamable-HTTP-style JSON-RPC facade over the existing
external Engine API surface. It is protected by the same ArchDox Engine API key
used by `/api/v1/engine/external/**`; JWT SaaS tokens are intentionally not used
for this endpoint.

Implemented MCP methods:

```text
initialize
ping
tools/list
tools/call
```

Implemented tools:

```text
create_review_session
submit_document
submit_context_facts
normalize_context
run_validation
get_review_result
validate_inspection_report
get_legal_updates
search_law
get_law_article
```

The current V1 implementation uses a small MCP tool registry rather than raw
controller branching. Each tool definition carries:

```text
tool name
capability
required scope
read/write access mode
request units
gateway-managed usage flag
input schema
handler
```

Gateway-managed tools such as `get_legal_updates`, `search_law`, and
`get_law_article` now pass quota checks and record usage under their own
capabilities (`LEGAL_UPDATES` or `LEGAL_SEARCH`). Review-session tools reuse the
external review-session service facade so REST and MCP keep the same
quota/usage behavior for the underlying engine operations.

The first legal corpus tools are intentionally read-only and source-backed:

```text
search_law
  -> searches the synchronized ArchDox legal corpus
  -> returns bounded article snippets and source/version metadata

get_law_article
  -> reads one synchronized article by articleVersionId, articleId, or actCode + articleNo
  -> returns the full article text plus source/version metadata
```

They read from ArchDox's synchronized corpus, not from live law.go.kr calls, and
exclude development fake-source rows from external results.

MCP tool-call usage metadata includes the MCP source, tool name, capability,
required scope, access mode, JSON-RPC id, correlation id, remote IP, and
user-agent when available. Failed or denied tool calls are recorded with
`FAILED` or `DENIED` status and `0` request units so platform admins can debug
scope and quota problems without charging denied calls as successful usage.

JSON-RPC errors preserve ArchDox internal error codes under `error.data.code`.
The current error data contract is:

```text
code        ArchDox internal error code
category    stable external category
retryable   whether a client may retry without changing input/auth/scope
messageKey  ArchDox localization/debug key when available
params      structured error context
```

For example, scope failures return `ENGINE_API_SCOPE_REQUIRED` with category
`SCOPE_REQUIRED`, quota failures return `ENGINE_API_DAILY_QUOTA_EXCEEDED` with
category `QUOTA_EXCEEDED`, unknown tools return `MCP_TOOL_NOT_FOUND`, and
malformed MCP params return `MCP_INVALID_PARAMS`.

`validate_inspection_report` is a convenience wrapper for early Codex/Cursor
testing. It creates a review session, submits optional document text/facts, and
runs validation. Long-running or state-mutating business work must still go
through ArchDox Worker action registry, policy gate, Flower execution, and
operation audit.

This is still not a production OAuth MCP gateway. It is the smallest useful
MCP-compatible adapter that proves external agents can call ArchDox Engine
through scoped keys without touching internal SaaS DTOs or raw database state.

### Phase MCP-6: Agent Connect Bootstrap

Add provisioning API, tool-pack registry, scope policy, approval screen, and
connection session tracking.

### Phase MCP-7: OAuth, Usage, And Audit

Add production-grade auth, token validation, tenant-scoped tool filtering,
usage metering, and audit logs.

## Non-Negotiable Rules

```text
MCP is an adapter, not the engine.
External agents must not create internal ArchDox DTOs.
ArchDox owns final normalization and rule selection.
Expose public canonical schema, not internal entities.
Bootstrap returns connection information, not unrestricted credentials.
Customer approval and scoped auth are required before tool calls.
Internal SaaS calls the Engine Boundary directly and does not need MCP.
```

## Process Split Policy

Do not split `archdox-engine-service` into a separate process yet.

Current implementation should stay physically inside `cloud-api` while keeping
the following package boundaries:

```text
com.archdox.cloud.engine.api
com.archdox.cloud.engine.application
com.archdox.cloud.engine.context
com.archdox.cloud.engine.domain
com.archdox.cloud.engine.infra
```

Reasons:

```text
authentication and user context are still shared with SaaS
legal/domain data is still being stabilized
MCP is not production-exposed yet
usage/billing/quota models are not complete
separate deployment would increase operational overhead too early
```

Extract only when one or more of these become true:

```text
external Engine/MCP traffic is significant
MCP customers need independent uptime or deployment cadence
legal indexing/review jobs affect SaaS latency
engine billing/quota/isolation becomes a product requirement
cloud-api package boundaries are stable enough to move without redesign
```

# Legal Domain Architecture

## Decision

ArchDox should manage legal and regulatory knowledge as a platform domain
asset, but it should not start as a separate server.

The initial implementation belongs inside `cloud-api` as an isolated
`legal` domain package:

```text
cloud-api
  -> legal
     -> legal corpus tables
     -> legal source connector
     -> legal sync/diff services
     -> Flower flows
     -> platform admin read APIs
```

The package must be designed so it can later be extracted into a separate
process:

```text
archdox-legal-worker
```

This is different from `archdox-agent`. `archdox-agent` is a document and file
execution server. The legal worker is a platform background worker for legal
source synchronization, change detection, impact analysis, and compliance
review support.

## Why Not Split The Project Now

Starting inside `cloud-api` is the right MVP choice because legal data is
mostly platform-wide shared data, not a local office/NAS execution concern.

| Option | Use Now | Reason |
| --- | --- | --- |
| `cloud-api` internal `legal` package | Yes | Simple deployment, shared DB transaction model, direct admin/API integration. |
| Separate `archdox-legal-worker` process | Later | Useful only when sync/diff/index/AI workload becomes heavy or needs independent operations. |
| `archdox-agent` | No | Agent handles document rendering, file movement, and local/cloud storage, not legal corpus management. |
| Generic external RAG service | No | Too broad. ArchDox needs verified legal corpus, versioning, diff, domain mapping, and audit first. |

The important rule is not physical process separation. The important rule is
architectural separation:

```text
legal source connector
-> legal corpus/version store
-> legal diff/change set
-> legal-domain binding
-> compliance review context
-> AI harness or deterministic review
```

## Source Of Truth

ArchDox should not invent legal text or rely on AI memory.

The source of truth should be an official public legal data source. For Korean
law data, the intended source is the National Law Information Open API.

Reference:

- https://open.law.go.kr/LSO/openApi/guideList.do
- https://open.law.go.kr/LSO/information/service.do

The API guide currently exposes law list/body APIs, law history APIs, article
history APIs, article-level body APIs, administrative rules, local ordinances,
precedents, legal interpretations, appendices/forms, legal terms, and related
law APIs. ArchDox should consume only the endpoints it explicitly needs.

## National Law Open API Usage

ArchDox currently uses the official National Law Information Open API through
the `www.law.go.kr/DRF` endpoint family.

The official guide exposes two important URL shapes:

```text
lawSearch.do
  -> list/search APIs

lawService.do
  -> body/detail APIs
```

### Currently Used Calls

ArchDox MVP intentionally tracks a narrow construction-supervision corpus.
Do not ingest the whole legal database.

```text
GET /DRF/lawSearch.do
  ?OC={apiKey}
  &target=law
  &type=JSON
  &query=건축법
  &display=10

GET /DRF/lawService.do
  ?OC={apiKey}
  &target=law
  &type=JSON
  &ID={법령ID}

GET /DRF/lawSearch.do
  ?OC={apiKey}
  &target=admrul
  &type=JSON
  &query=건축공사 감리세부기준
  &display=10

GET /DRF/lawService.do
  ?OC={apiKey}
  &target=admrul
  &type=JSON
  &ID={행정규칙일련번호}
```

Tracked assets:

| target | query | ArchDox actCode | Purpose |
| --- | --- | --- | --- |
| `law` | `건축법` | `BUILDING_ACT` | Core building-law corpus. |
| `law` | `건축법 시행령` | `BUILDING_ACT_ENFORCEMENT_DECREE` | Enforcement decree corpus. |
| `law` | `건축법 시행규칙` | `BUILDING_ACT_ENFORCEMENT_RULE` | Enforcement rule corpus. |
| `admrul` | `건축공사 감리세부기준` | `CONSTRUCTION_SUPERVISION_DETAILED_STANDARD` | Construction-supervision administrative rule and appendices. |

### Useful API Surface Not Yet Consumed

The official guide also exposes many related targets. These are candidates, not
current MVP behavior:

| API category | Example target | Use later |
| --- | --- | --- |
| Law detail by effective date | `eflaw` | Compare effective-date-specific versions. |
| Article/subarticle detail | `lawjosub`, `eflawjosub` | Fetch a bounded article instead of a full law body. |
| Administrative rules | `admrul` | Already used for construction-supervision detailed standards. |
| Local ordinances | `ordin` | Future regional/local permitting support. |
| MOLIT legal interpretations | `molitCgmExpc` | Future source-backed interpretation references. |
| Intelligent legal search | `aiSearch` | Future search discovery only; do not use as source of truth without resolving exact source ids. |
| Appendices/forms | law/admin-rule appendix APIs | Future official form and checklist matching. |

### Response Shape Used By ArchDox

For `target=law`, ArchDox reads:

```text
법령
  기본정보
    법령ID
    법령명_한글
    법종구분
    공포번호
    공포일자
    시행일자
    소관부처
    제개정구분
  조문
    조문단위[]
      조문여부
      조문키
      조문번호
      조문제목
      조문내용
      조문시행일자
```

For `target=admrul`, ArchDox reads:

```text
AdmRulService
  행정규칙기본정보
    행정규칙명
    행정규칙일련번호
    발령번호
    발령일자
    시행일자
    소관부처명
    제개정구분명
  조문내용
  별표
    별표단위[]
      별표키
      별표번호
      별표제목
      별표내용
      별표서식PDF파일링크
```

Implementation note: official responses can be large, occasionally reset the
connection, and may not always provide perfectly unique source keys. The
connector therefore:

- uses JSON only for now
- retries transient response-read failures and retryable HTTP status codes
  such as 429 and 5xx
- throttles requests between calls
- rejects official API error payloads before they reach corpus storage
- rejects target snapshots that have no article/body/annex content
- preserves official source ids in metadata
- normalizes duplicate article keys with a stable suffix such as `_2`
- stores hashes and versions in ArchDox, not raw unbounded response logs

### API Calling Discipline

Legal API calls are platform operations, not user-facing search calls.

Rules:

- Never call the official API directly from AI, UI, or `archdox-agent`.
- Never run broad crawling against all laws during MVP.
- Never call the official API from report preflight review.
- Sync only configured tracked assets.
- Use DB-backed corpus and change sets for UI, AI context, and user notices.
- Manual sync is allowed from platform admin.
- Manual Open API sync must be blocked before creating a sync run unless the
  connector is enabled, OC is configured, and at least one exact target exists.
- Scheduled sync is allowed later only as a trigger that submits Flower flow.
- Keep `display` small for exact-name search.
- Keep request interval and retry count configurable.
- Sync failures should preserve stable failure codes such as
  `LAW_OPEN_DATA_HTTP_429`, `LAW_OPEN_DATA_RESPONSE_ERROR`, and
  `LAW_OPEN_DATA_ARTICLES_EMPTY` in `legal_sync_runs`.

Current defensive defaults:

```yaml
LEGAL_OPEN_API_REQUEST_TIMEOUT_MS=20000
LEGAL_OPEN_API_REQUEST_INTERVAL_MS=800
LEGAL_OPEN_API_MAX_ATTEMPTS=3
```

If a larger corpus is added, increase the interval or move sync execution to a
future `archdox-legal-worker` process with stricter rate limiting and circuit
breaker policy.

## Legal Positioning Rule

ArchDox must not present AI output as legal advice or final legal judgment.

The legal domain is not a generic law-search feature. The business value is
connecting source-backed legal and administrative-rule data to ArchDox business
context:

```text
document type
project/site condition
construction supervision domain catalog
work type and checklist item
review effective date
office/customer mapping profile
```

So the product should be described as:

```text
source-backed compliance and missing-context review for architecture-office
workflows
```

not:

```text
automatic legal advice
```

The system may provide:

- source-backed compliance risk findings
- missing-evidence warnings
- wording and audit-risk hints
- change impact summaries
- references to official source ids, article ids, dates, and excerpts

The system must not provide:

- invented law citations
- unstated article numbers
- final legal conclusions without source evidence
- hidden AI-only legal decisions
- direct AI access to unrestricted production data

Every legal/compliance finding should include:

```text
source law
article or rule reference
effective date
matched business context
finding severity
human-readable reason
whether human confirmation is required
```

## Target Architecture

```text
Official legal API
  -> LegalSourceClient
  -> LegalSyncFlow
  -> LegalCorpusStore
  -> LegalDiffFlow
  -> LegalChangeSet
  -> LegalDomainBinding
  -> ComplianceReviewContextBuilder
  -> ConstructionComplianceReviewHarness
  -> ReportPreflightReviewWorker / future WorkerTaskOrchestrationFlow
```

### Responsibilities

| Layer | Responsibility |
| --- | --- |
| `LegalSourceClient` | Talks to official legal APIs. No domain decisions. |
| `LegalCorpusStore` | Stores law, version, article, text hash, source metadata, and effective dates. |
| `LegalDiffService` | Normalizes text, compares hashes, creates article-level changes. |
| `LegalDomainBindingService` | Maps legal articles/rules to ArchDox domain concepts such as report type, catalog item, checklist item, or supervision ledger concept. |
| `ComplianceReviewContextBuilder` | Builds small source-backed context packets for deterministic validation and AI review. |
| `ConstructionComplianceReviewHarness` | Reviews bounded context with AI, returns structured findings only. |
| `ReportPreflightReviewWorker` | Uses deterministic validation first, then legal/AI review when policy allows. |
| `Platform Admin UI/API` | Lets operators inspect legal sync status, changes, impact, and review findings. |

## Proposed Data Model

These tables are platform/global by default. They should not have `office_id`
unless the row is explicitly an office-specific override or binding.

```text
legal_sources
legal_acts
legal_versions
legal_articles
legal_article_versions
legal_change_sets
legal_article_diffs
legal_change_digests
legal_domain_bindings
legal_review_runs
legal_review_findings
```

### Table Intent

| Table | Intent |
| --- | --- |
| `legal_sources` | Official source configuration and connector metadata. |
| `legal_acts` | Tracked law/rule identity, such as Building Act or administrative rule. |
| `legal_versions` | One captured version of one law/rule by source date/effective date. |
| `legal_articles` | Stable article identity inside a law/rule. |
| `legal_article_versions` | Captured article text, normalized hash, effective date, and source metadata. |
| `legal_change_sets` | Git-like legal change event between two captured versions. |
| `legal_article_diffs` | Article-level added/removed/modified summary and before/after hashes. |
| `legal_change_digests` | Human-readable change notice generated from a change set. This is what users see as a legal update post. |
| `legal_domain_bindings` | Mapping from legal articles/rules to ArchDox report/workflow/catalog/checklist concepts. |
| `legal_review_runs` | Compliance review execution record. |
| `legal_review_findings` | Deterministic or AI-assisted compliance findings with source references. |

## Git-Like Change Model

Legal changes should be tracked like a versioned ledger.

Example concept:

```json
{
  "changeSetId": 120,
  "source": "NATIONAL_LAW_OPEN_API",
  "actCode": "BUILDING_ACT",
  "previousVersionId": 10,
  "newVersionId": 11,
  "detectedAt": "2026-06-03T00:00:00Z",
  "effectiveDate": "2026-07-01",
  "summary": "3 articles modified, 1 appendix changed",
  "articleDiffs": [
    {
      "articleNo": "25",
      "changeType": "MODIFIED",
      "beforeHash": "sha256:...",
      "afterHash": "sha256:..."
    }
  ]
}
```

The hash is important. It lets ArchDox detect whether a text body really
changed even if source metadata is noisy.

## Flower Flow Design

Legal operations should not be implemented as ad-hoc scheduler logic.

The scheduler may trigger work, but business orchestration belongs to Flower.

```text
Scheduler or admin button
  -> submit LegalSyncFlow
  -> FetchTrackedActsStep
  -> FetchActVersionStep
  -> NormalizeArticlesStep
  -> StoreVersionStep
  -> DetectDiffStep
  -> StoreChangeSetStep
  -> EmitOperationEventStep
```

For impact analysis:

```text
LegalChangeSetCreated
  -> StoreDeterministicLegalChangeDigestStep
  -> submit LegalImpactAnalysisFlow
  -> ResolveDomainBindingsStep
  -> BuildImpactSummaryStep
  -> OptionalAiImpactHarnessStep
  -> StoreImpactFindingsStep
  -> NotifyPlatformOpsStep
```

The digest layer is intentionally separate from raw diff storage:

```text
legal_change_sets + legal_article_diffs
  -> legal_change_digests
  -> user-facing legal update list/detail
  -> future notification fan-out
```

`legal_change_digests` should stay readable and bounded. It can start with a
deterministic title/summary, then later be enriched by an AI worker that reads
the source-backed diff and produces a clearer title, impact summary, and
affected ArchDox domain bindings. Notifications should be triggered only after
the digest is published; notification delivery is a channel concern, not the
source of truth.

The current deterministic digest is intentionally conservative: it summarizes
article change counts, includes a small set of representative article labels,
keeps the raw change-set summary as source context, and fills construction
supervision report/catalog bindings for Building Act or supervision-related
acts. This gives user and admin screens a useful post before AI impact analysis
exists, while keeping the future AI worker as an enrichment layer instead of
the source of truth. Existing deterministic digests may be refreshed from the
same raw change set, but AI-sourced digests must not be overwritten by the
deterministic fallback. Platform admin exposes a deterministic digest refresh
action for this maintenance path so existing corpus data can be reprojected
after digest wording or domain-binding rules improve.

For report review:

```text
Report preflight review
  -> deterministic report validation
  -> build legal context from report type/catalog/checklist entries
  -> optional ConstructionComplianceReviewHarness
  -> persist legal findings
  -> merge with preflight review status
```

## Worker And Harness Boundary

`archdox-worker` controls whether a user/system action may happen.
`archdox-ai-harness` controls reliable AI execution for one bounded AI task.

For legal/compliance:

```text
archdox-worker
  -> action policy, permission, approval, trace

cloud-api legal package
  -> legal corpus, source sync, diff, domain bindings

archdox-ai-harness
  -> ConstructionComplianceReviewHarness
```

AI is not allowed to call the official legal API directly. AI receives a
bounded context packet prepared by Cloud API.

## Scheduling Policy

Scheduling is allowed only as a trigger, not as orchestration.

Allowed:

```text
@Scheduled
  -> if enabled, submit LegalSyncFlow
```

Not allowed:

```text
@Scheduled
  -> fetch law
  -> diff
  -> update DB
  -> call AI
```

This keeps the system consistent with ArchDox's Flower-based orchestration
direction.

## Configuration

Legal source configuration should be environment-based and disabled by default
until credentials are provided.

Example:

```yaml
archdox:
  legal:
    sync:
      worker-interval-ms: 250
      open-api:
        enabled: false
        oc: "${LEGAL_OPEN_API_OC:}"
        base-url: "https://www.law.go.kr/DRF"
        user-agent: "Mozilla/5.0 ArchDox/1.0"
        request-timeout-ms: 20000
        request-interval-ms: 800
        max-attempts: 3
```

The `OC` value is a platform secret. It must be configured on Cloud API or the
future legal worker only.

## Initial Tracked Legal Assets

Start narrow. Do not ingest every law.

Initial candidates:

- Building Act and related enforcement rules
- construction supervision administrative rules and detailed standards
- forms and appendices related to construction supervision
- selected MOLIT interpretations only when they are needed for review

The active MVP remains construction supervision. Demolition supervision,
facility inspection, and other domains should not be mixed into the first legal
binding set.

## Implementation Phases

### Phase L-0: Architecture Document

Create this document and update the documentation map.

Test:

- no code test
- `git diff --check` for Markdown hygiene

### Phase L-1: Legal Domain DB Foundation

Add migration tables for legal source, act, version, article, article version,
change set, article diff, domain binding, review run, and review finding.

Test:

- migration loads in `cloud-api` integration context
- unique constraints prevent duplicate act/version/article snapshots
- no `office_id` on global corpus tables

### Phase L-2: Legal Corpus Application Skeleton

Add domain/application package with:

```text
LegalSourceClient
LegalCorpusSyncService
LegalTextNormalizer
LegalArticleHashService
LegalDiffService
LegalDomainBindingService
```

Test:

- normalizer produces stable hash for whitespace-equivalent text
- diff service detects added/modified/removed article snapshots
- services do not call external API in unit tests

### Phase L-3: Manual Seed / Fake Source Connector

Before live API integration, add a fake or static source connector so the sync
flow can be tested deterministically.

Test:

- first sync creates legal version and article versions
- second identical sync creates no new change set
- modified fake article creates one change set and one article diff

### Phase L-4: Flower LegalSyncFlow

Implement the sync flow using Flower steps.

Test:

- flow completes and records operation events
- retry/backoff policy is attached to fetch and store steps
- sync failure is visible in platform ops

### Phase L-5: National Law Open API Connector

Attach official API connector behind `LegalSourceClient`.

Test:

- disabled when credential/config is absent
- mock HTTP test for XML/JSON parsing
- live smoke test only when credential is present

### Phase L-6: Legal Domain Binding

Bind legal articles/rules to construction supervision report types, catalog
items, checklist entries, or supervision ledger concepts.

Test:

- daily supervision report resolves only construction-supervision legal context
- demolition/safety deferred domains are not returned
- bindings are versioned/effective-date aware

### Phase L-7: Compliance Review Harness

Add `ConstructionComplianceReviewHarness` in `archdox-ai-harness`.

Test:

- fake AI returns structured legal findings
- invalid/no-citation output fails validation or is downgraded
- deterministic findings still run before AI

### Phase L-8: Report Preflight Integration

Connect legal context into report preflight review when office AI/legal review
policy allows it.

Test:

- no legal AI call when policy disabled
- deterministic legal rules still run
- legal findings show source references and do not claim final legal advice

### Phase L-9: Platform Admin Legal Ops UI

Add platform admin screens:

```text
Legal sources
Tracked laws
Latest sync status
Change sets
Article diffs
Domain bindings
Compliance review findings
```

Test:

- platform admin only
- office admin cannot access platform legal corpus management
- lists are paginated and do not load unbounded logs

### Phase L-9-1: User-Facing Legal Update Digest

Expose recent published legal change digests to authenticated users.

The user screen should behave like a lightweight notice board:

```text
list recent legal update titles by date
-> click a digest
-> read summary, effective date, and business impact note
```

Test:

- normal authenticated user can read published digests
- raw legal diff internals remain platform-admin only
- list has bounded `days` and `limit`
- future notification work can fan out from published digest rows

### Phase L-10: Extract To `archdox-legal-worker` If Needed

Only extract when operational pressure justifies it.

Extraction should preserve:

- same DB tables
- same service interfaces
- same Flower flow names
- same operation event semantics
- Cloud API read/admin APIs

## Extraction Criteria

Create `archdox-legal-worker` only when at least one condition is true:

- sync/diff/index jobs slow down user-facing Cloud API
- live legal API calls need isolated retry/timeout/circuit breaker operations
- AI legal impact analysis becomes expensive or long-running
- legal corpus indexing requires different runtime resources
- operations need independent deployment/restart of legal jobs

Before that point, a separate server is unnecessary complexity.

## Security And Audit

- Legal API credentials are platform secrets, never office/user secrets.
- AI prompts receive source-backed excerpts only, not unrestricted DB access.
- Raw legal source responses may be retained only if storage policy allows it.
- All sync/change/review jobs write operation events.
- Compliance findings must include source metadata and effective date.
- Platform admin legal operations must be audited.

## Non-Goals

Do not implement these in the first legal phase:

- full legal search portal
- automatic legal advice
- unrestricted RAG over all laws
- vector indexing before structured corpus/diff exists
- user-editable legal text
- office-specific legal overrides before platform corpus is stable
- hidden AI calls without policy, budget, and trace

## Summary

The first implementation should be:

```text
cloud-api internal legal package
+ platform/global legal DB tables
+ fake/static source sync
+ Flower sync/diff flow
+ platform admin read visibility
```

The future implementation may become:

```text
archdox-legal-worker
+ independent sync/diff/impact/AI legal jobs
+ Cloud API read/admin APIs
+ same DB-backed legal corpus
```

This gives ArchDox the right path: simple enough for MVP, but strong enough to
become a real architecture-office compliance and legal-change awareness layer.

## Current Implementation Status

As of 2026-06-04, the legal domain foundation has started inside `cloud-api`.

Implemented:

- `V42__legal_domain_foundation.sql`
- `V43__legal_change_digests.sql`
- `legal_sources`
- `legal_acts`
- `legal_versions`
- `legal_articles`
- `legal_article_versions`
- `legal_sync_runs`
- `legal_change_sets`
- `legal_article_diffs`
- `legal_change_digests`
- `legal_domain_bindings`
- `legal_review_runs`
- `legal_review_findings`
- `LegalSourceClient`
- `FakeLegalSourceClient`
- `LawOpenDataLegalSourceClient`
- `LegalTextNormalizer`
- `LegalArticleHashService`
- `LegalDiffService`
- `LegalCorpusSyncService`
- `LegalSyncFlowFactory`
- `LegalSyncWorker`
- platform-admin backend API for fake sync and readback:

```text
POST /api/v1/platform-admin/legal/sync/fake
POST /api/v1/platform-admin/legal/sync/open-data
GET  /api/v1/platform-admin/legal/sync-runs
GET  /api/v1/platform-admin/legal/change-sets
GET  /api/v1/platform-admin/legal/change-digests
GET  /api/v1/platform-admin/legal/open-api/status
GET  /api/v1/legal-updates
GET  /api/v1/legal-updates/{id}
```

Real Open API integration status:

- `LEGAL_OPEN_API_ENABLED=false` by default
- `LEGAL_OPEN_API_OC` required for live sync
- exact target list is configured in `LegalSyncProperties`
- live sync is platform-admin/manual for now
- platform admins can inspect Open API enabled/credential/target status through
  `/api/v1/platform-admin/legal/open-api/status`
- the status response includes `ready`, target count, and estimated API request
  count; manual live sync is rejected with `LEGAL_OPEN_API_NOT_READY` when it is
  not ready
- live HTTP sync retries 429/5xx and response-read failures, throttles calls,
  and records stable failure codes into failed sync runs
- current live smoke result on 2026-06-04 synchronized 4 tracked assets and
  created 462 article diffs

Not implemented yet:

- scheduler trigger
- legal domain binding admin UI
- compliance review harness
- report preflight legal integration
- legal update notification fan-out
- separate `archdox-legal-worker` process

The fake source is still intentionally development-only. It exists so the
corpus persistence, hash normalization, diff detection, Flower flow, and admin
read APIs can be tested without consuming official API calls. Published digests
are currently deterministic summaries; AI-generated legal impact titles and
explanations are a later worker/harness phase.

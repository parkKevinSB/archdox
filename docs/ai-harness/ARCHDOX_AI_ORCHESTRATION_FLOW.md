# ArchDox AI Orchestration Flow

## Purpose

ArchDox is a document workflow platform. It must own the business-level AI
orchestration flow.

`flower-ai-harness` owns one safe AI execution unit. ArchDox owns when to run
that unit, how many harnesses to compose, how to compare their results, and how
the document/report state changes afterward.

## Two Flow Layers

There are two different Flower-based flow layers.

```text
ArchDox Business Flow
  -> document review orchestration
  -> legal review orchestration
  -> UI/report/job state decisions
  -> operation events

AI Harness Flow
  -> prepare-prompt
  -> await-response
  -> validate-response
  -> refine-decision
  -> emit-findings
```

The AI Harness Flow is a reusable internal block. It should not know ArchDox
business process decisions such as whether a report can be approved, regenerated,
or sent back for correction.

The ArchDox Business Flow is the business workflow. It may run one harness, wait
for it, run another harness, compare results, rerun a harness, or mark the
document review as completed.

## What One ArchDox AI Orchestration Flow Owns

One ArchDox orchestration flow should own one business operation, not one raw AI
call.

Examples:

```text
DocumentReviewFlow
  owns one document AI review operation for one document job.

ReportPreflightReviewFlow
  owns one pre-generation review operation for one inspection report.

LegalReviewFlow
  owns one legal/business-rule review operation for one report/document.

TemplateOnboardingFlow
  owns one template/config proposal operation from a reference document.

PlatformOpsDiagnosisFlow
  owns one operational diagnosis operation for stuck jobs, Agent health,
  repeated failures, abnormal logs, AI cost anomalies, or security spikes.
```

For MVP, `DocumentReviewFlow` should own:

- loading the document job, report, artifacts, snapshot, and office context
- creating a `document_ai_review_runs` row
- submitting `DocumentQaHarness`
- waiting for the harness run to finish
- reading findings
- deciding `PASSED`, `NEEDS_ATTENTION`, or `FAILED`
- recording operation events
- exposing progress to the UI/admin through persisted state

It should not:

- directly call OpenAI/Ollama/Spring AI
- parse provider-specific AI responses
- implement JSON schema retry/refine logic
- change report facts automatically without user approval
- render documents itself

## Suggested DocumentReviewFlow Steps

The first ArchDox-level AI orchestration flow should be intentionally small.

```text
load-review-context
run-deterministic-validation
submit-document-qa-harness
await-document-qa-harness
summarize-document-qa-result
complete-document-review
```

Step responsibilities:

```text
load-review-context
  Validate office/user access, document job status, report existence, and
  artifact availability.

run-deterministic-validation
  Run exact code checks before any AI call. Check snapshot shape, generated
  artifact existence, output format consistency, artifact metadata, and required
  working photo assets. If blocking findings exist, persist them and skip AI.

submit-document-qa-harness
  Submit the DocumentQaHarness only when deterministic validation did not
  already complete the run with blocking findings.

await-document-qa-harness
  Poll persisted harness run status. Stay while QUEUED/RUNNING/REFINING.
  Continue when SUCCEEDED/FAILED/CANCELLED.

summarize-document-qa-result
  Read findings and derive a business result:
  PASSED when no blocking findings exist.
  NEEDS_ATTENTION when findings exist but the harness succeeded.
  FAILED when the harness itself failed or was cancelled.

complete-document-review
  Persist final review status and operation event. Do not mutate report facts.
```

The same structure can later expand:

```text
load-review-context
submit-document-qa-harness
await-document-qa-harness
submit-legal-review-harness
await-legal-review-harness
reconcile-review-results
maybe-rerun-document-qa
complete-document-review
```

## Harness As Child Work

An ArchDox orchestration flow should treat each harness as child work.

```text
ArchDox step
  -> creates harness run
  -> submits AiHarnessFlow
  -> persists run id
  -> waits through DB state
```

The parent flow should not block a thread waiting for the AI provider. It should
advance by Flower ticks and persisted state.

The harness run id is the link between the parent business flow and the internal
AI harness flow.

```text
document_review_flow_runs.current_harness_run_id
-> document_ai_review_runs.harness_run_id
```

MVP may keep the parent flow state inside existing `document_ai_review_runs` if a
separate parent table is premature. Once multiple harnesses are composed, create
a parent table such as:

```text
document_review_runs
document_review_run_steps
document_ai_review_runs
document_ai_review_findings
```

## Flow Boundary Rule

Use this rule when deciding whether something belongs in `flower-ai-harness` or
ArchDox.

Belongs in `flower-ai-harness`:

- prompt rendering for a single harness run
- provider call lifecycle
- response polling
- schema validation
- retry/refine/model fallback
- cancellation for one AI run
- finding extraction for one validated AI response

Belongs in ArchDox:

- selecting which harnesses to run
- deciding when to run/re-run a harness
- comparing QA, legal, photo, and template findings
- updating document/report/review status
- exposing progress to UI/admin
- recording operation events
- enforcing office/user/project/report permissions

## Platform Ops AI Flow Policy

Operations AI follows the same two-layer rule.

```text
PlatformOpsDiagnosisFlow
  -> load platform admin authorization
  -> collect operation_events/read-model summaries
  -> run deterministic detectors
  -> build redacted ops snapshot
  -> submit OpsDiagnosisHarness when useful
  -> wait for harness completion
  -> persist ops findings/incidents
  -> expose result to platform admin
```

The ArchDox flow owns the operational decision. `flower-ai-harness` only owns
one safe AI diagnosis execution.

The ops flow must not:

- send raw logs, secrets, signed URLs, device tokens, or file contents to AI
- let AI mutate DB state directly
- execute repair actions without platform admin approval
- replace deterministic stuck/error/security detectors with AI guesses

The ops flow may:

- correlate operation events, stuck DB states, summarized logs, and AI cost
  records
- ask AI to explain likely causes and recommended next checks
- create findings and incident summaries for platform admin review
- suggest a retry, ignore, notify, or manual investigation action

This keeps platform operations consistent with the ArchDox workflow model:
detect, diagnose, record, review, then act with explicit authority.

Current implementation note:

`PlatformOpsDetectionFlow` creates detector incidents/findings. A platform admin
can then submit `PlatformOpsDiagnosisFlow` for one incident. The diagnosis flow
builds a redacted deterministic snapshot and records an
`OPS_DIAGNOSIS_SNAPSHOT_READY` finding with source `SYSTEM_DIAGNOSIS`.

If `archdox.platform-admin.ops.ai-diagnosis.enabled=true` and a provider/model
are configured, the same parent flow submits child `OpsDiagnosisHarness` work to
the shared `ai-harness` Flower worker lane, waits for the harness terminal
state, and stores AI findings with source `AI_HARNESS`. The platform ops
workflow type remains separate from the worker lane name. If the option is
disabled or misconfigured, the flow completes deterministically without calling
AI.

## User-Facing Document Flow Policy

Keep AI review and deterministic document generation as separate user-visible
operations.

Recommended user flow:

```text
Report draft
-> user completes required steps/photos/checklists
-> READY_TO_GENERATE
-> deterministic validation
-> optional pre-generation AI review
-> user fixes issues or accepts suggestions
-> signature/confirmation
-> deterministic document generation
-> deterministic artifact validation
-> optional post-generation document QA
-> download/delivery/history
```

Rules:

- Deterministic validation always runs before AI. Code must handle checks that
  are exact and cheap: required fields, status transitions, missing photos,
  missing working images, missing artifacts, invalid metadata, template binding
  gaps, and output format consistency.
- If deterministic validation finds blocking issues, do not call the AI model.
  Persist findings and ask the user to fix the concrete problem first.
- Pre-generation AI review checks report input completeness, checklist gaps,
  photo evidence, required fields, and legal/business-rule risks before a final
  document is generated.
- Text polish is a draft-assist flow. It may propose improved wording, but it
  must not silently mutate report facts or generated documents.
- Document generation must remain deterministic from:

```text
report snapshot + template config + output layout
```

- If AI-generated wording is accepted, it becomes part of the report draft or a
  signed snapshot before document generation.
- Post-generation document QA checks whether the generated output/artifacts look
  coherent. It does not replace pre-generation business validation.

This keeps the user experience simple while keeping accountability clear.

## Cost Control Rule

AI is the expensive reviewer, not the first validator.

```text
deterministic checks
-> blocking finding? persist finding and stop
-> no blocking finding? build small review packet
-> run AI harness
-> persist AI findings
```

Do not send raw or oversized data to AI when code can decide the issue. The
review packet should contain only the snapshot, layout summary, artifact summary,
photo metadata, deterministic validation result, and document-type rules needed
for the harness.

## Finding Resolution Policy

AI and deterministic review findings are not only messages. They are workflow
items that the user must either fix or explicitly accept before generation can
continue.

Initial statuses:

```text
OPEN
RESOLVED
ACCEPTED
```

Rules:

- New findings are created as `OPEN`.
- `RESOLVED` means the user says the underlying report input was fixed.
- `ACCEPTED` means the user intentionally accepts the risk and proceeds.
- `HIGH` and `CRITICAL` findings block generation while they are `OPEN`.
- When all blocking findings in a preflight run are `RESOLVED` or `ACCEPTED`,
  the run can be marked `PASSED`.
- Resolution changes must record who handled the finding and when it was handled.

This keeps AI review accountable. The system does not silently rewrite report
facts, but it does remember whether the user corrected the issue or accepted the
remaining risk.

## Preflight Revision Gate Policy

Document generation must be gated by the latest submitted report revision.

Rules:

- `ReportPreflightReviewFlow` stores the `reportRevision` it reviewed.
- `POST /api/v1/inspection-reports/{reportId}/document-jobs` must require a
  `PASSED` preflight run for `InspectionReport.generationRevision()`.
- A `PASSED` run for an older revision is history only. It must not unblock
  document generation after the report is reopened, edited, and submitted as a
  newer revision.
- If no passed run exists for the current generation revision, return
  `REPORT_PREFLIGHT_REVIEW_REQUIRED`.
- If a passed run exists only for an older revision, return
  `REPORT_PREFLIGHT_REVIEW_STALE`.
- The UI should show stale preflight results as stale, disable generation, and
  ask the user to rerun preflight review for the current revision.

This prevents a user from fixing or changing a report after review and then
generating a document based on an old approval.

## AI Feature Gate Policy

AI features must be explicitly enabled. They are optional workflow capabilities,
not mandatory infrastructure.

Initial gate:

```text
DOCUMENT_AI_REVIEW_ENABLED=false
```

Current runtime gate:

```text
archdox.document-ai.review.enabled
-> office_ai_policies.ai_enabled
-> office_ai_policies.document_review_ai_enabled
-> office_ai_policies.preferred_provider_credential_id
-> ai_provider_credentials.status = ACTIVE
-> ai_provider_credentials.default_model
```

The resolved model is passed to `flower-ai-harness` as:

```text
ModelId = {providerCode}:{defaultModel}
```

Example:

```text
openai-main:gpt-4.1-mini
```

The parent ArchDox flow owns the business operation. The child harness flow owns
one safe AI execution unit. The provider call itself is made through the
`AiModelGateway` port, backed in ArchDox by platform-managed provider
credentials. Cloud API must run those provider calls through a bounded executor,
not the JVM common pool. The current ArchDox gateway uses the
`ai-model-gateway-*` executor and fails fast with an observable gateway overload
when the configured queue is full.

Future gates may add user permission, plan quota, and workflow definition
constraints.

Examples:

- An office may enable pre-generation AI review but disable text polish.
- A personal plan may allow basic review but not legal review.
- A workflow may require human review before AI suggestions are applied.

The API must reject disabled AI operations before creating expensive work.

## Implementation Phases

### Phase AI-O1: DocumentReviewFlow Foundation

Create an ArchDox-level `DocumentReviewFlow` that wraps the existing
`DocumentQaHarness` run.

Expected result:

```text
REST request
-> create review operation
-> submit DocumentReviewFlow
-> flow submits DocumentQaHarness
-> UI polls review status/findings
```

This phase should preserve the current public API where possible, but internally
move orchestration out of the controller/service direct-submit path.

### Phase AI-O2: ReportPreflightReviewFlow Foundation

Create a report-based pre-generation review flow.

Expected result:

```text
POST /api/v1/inspection-reports/{reportId}/preflight-review-runs
-> create report preflight review run
-> submit ReportPreflightReviewFlow
-> deterministic validation reuses submit validation rules
-> findings are stored for UI polling
```

Initial scope:

- required workflow steps
- required fields
- checklist saved state
- minimum working photos
- report state conflicts
- non-blocking target selection warning

This flow must run before document generation. It is allowed to finish without
an AI call when code-level findings are enough. A later phase can add a child
`ReportPreflightHarness` after deterministic checks pass.

### Phase AI-O3: Persist Parent Document Review State

Add parent review state if needed:

```text
document_review_runs
document_review_run_steps
```

Do this when a single `document_ai_review_runs` row is no longer enough to
represent multiple harness runs or reconciliation decisions.

### Phase AI-O4: LegalReviewHarness Composition

Add `LegalReviewHarness` as a second child harness and compose it inside
`DocumentReviewFlow`.

Expected flow:

```text
Document QA
-> Legal Review
-> Reconcile
-> Complete
```

### Phase AI-O5: Recheck And Rerun Policy

Add explicit policy for when a previous harness should run again.

Examples:

- Legal review finds missing statutory basis, so document QA is rerun with
  additional context.
- Photo evidence review finds missing image references, so document QA is rerun
  after artifact metadata is refreshed.

Reruns must be visible in persisted run history. Do not silently replace old
findings.

## Current Status

Current implementation is `DocumentQaHarness` V1 plus ArchDox-level
`DocumentReviewFlow`.

The REST API creates a review run and submits `DocumentReviewFlow`. The parent
flow runs deterministic validation first. If no blocking code-level findings are
found, it submits the child `DocumentQaHarness`, waits for persisted harness
status, summarizes findings, and records operation events.

Current scope is post-generation document QA for an existing generated document
job. Pre-generation report review should be added as a separate report-based
flow, because it validates the user's draft before signature and document
generation.

Report-based pre-generation review is now available as
`ReportPreflightReviewFlow`.

Implemented V1 behavior:

```text
REST request
-> create report_preflight_review_runs row
-> optionally attach ReportPreflightHarness when AI policy allows it
-> run deterministic validation
-> if deterministic blocking findings exist, skip AI
-> if deterministic validation passes and AI is planned, submit child harness
-> wait for persisted harness terminal status
-> summarize findings into PASSED / NEEDS_ATTENTION / FAILED
-> UI polls run status and findings
```

`ReportPreflightHarness` is intentionally separate from `DocumentQaHarness`.
It reviews the user's draft report input before document generation. The
post-generation `DocumentQaHarness` reviews generated document artifacts after
generation.

`ReportPreflightHarness` V1 also includes lightweight legal/compliance review.
This is not a legal-advice engine. It checks whether the report input appears
defensible for an audit, dispute, or public-agency review and flags missing
evidence, vague safety/legal wording, contradictory checklist answers, and
report-type-specific compliance gaps.

The preflight AI issue schema includes:

```text
category = GENERAL | COMPLETENESS | CONSISTENCY | EVIDENCE | COMPLIANCE | LEGAL_RISK | WORDING
```

The category is stored in finding attributes so UI and operations can separate
ordinary QA findings from compliance/legal-risk findings without creating a
separate workflow yet.

Preflight findings now also have a user resolution workflow. Blocking findings
remain open until the user marks them resolved or accepts the risk. Once every
blocking finding is handled, the preflight run is recalculated as passed so the
document generation buttons can become available again.

Platform admins can read preflight findings across offices from the AI
operations surface. This is intentionally read-only in the platform console:
office users own the report decision, while platform admins monitor unresolved
or risk-accepted findings for support, audit, and abuse/cost review.

The platform AI screen should surface at least these operating signals:

- open blocking findings (`OPEN` + `HIGH`/`CRITICAL`)
- risk-accepted findings (`ACCEPTED`)
- compliance/legal-risk categories
- filters by resolution and severity

These signals help the operator see whether AI review is producing actionable
work, whether users are bypassing too many risks, and whether a specific office
needs workflow/template guidance.

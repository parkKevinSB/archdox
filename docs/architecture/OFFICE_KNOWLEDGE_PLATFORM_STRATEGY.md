# Office Knowledge Platform Strategy

ArchDox should grow beyond document generation into an office knowledge
platform for architecture offices.

The core insight:

```text
Documents are outputs.
Structured office workflow data is the long-term asset.
```

Inspection logs, supervision reports, photos, checklist answers, findings,
document jobs, artifacts, assignments, and operation events are not just
temporary inputs for DOCX/PDF generation. Over time, they become searchable
office memory.

## Product Thesis

ArchDox starts by helping users create supervision and inspection documents.
But the same structured data can later answer higher-value office questions:

```text
Summarize our office's supervision work over the last two years.
Find cases where a similar defect or safety issue happened before.
Which worker handled reports with this type of finding?
Show repeated issues by site type, project type, or checklist category.
Find reports where photo evidence was weak or missing.
Compare this new site with similar past sites.
Summarize recurring document generation or template failures.
```

This is only possible if ArchDox keeps domain data structured. If the system
only stored final DOCX/PDF files, future AI/search would be forced to parse
documents again and would lose much of the workflow context.

## Data As Office Knowledge

The following data should be treated as future knowledge assets:

| Data | Why It Matters Later |
| --- | --- |
| `projects` | Long-lived office business scope and performance grouping. |
| `sites` | Real work location and repeated operational context. |
| `inspection_targets` | Building/space/object-level work target. |
| `inspection_reports` | Main work record and revision lifecycle. |
| `inspection_report_steps` | Structured report facts, checklist answers, notes, and workflow stage data. |
| `checklist_answers` | Repeated measurable domain signals. |
| `photos` / `photo_assets` | Evidence and proof, tied to report/step/checklist context. |
| `report_preflight_review_findings` | Deterministic and AI-assisted quality/risk findings. |
| `document_jobs` / `document_artifacts` | Generated document history and output trace. |
| `operation_events` | Audit, operational problems, workflow transitions, and recovery history. |
| `archdox_worker_*` | Controlled worker action traces and user delegation history. |

Do not treat these tables as implementation leftovers for document generation.
They are the first version of ArchDox's office knowledge graph.

## Current Preparation Rules

These rules should guide near-term development even before search/vector/AI
knowledge features are implemented.

1. Keep report facts structured.

   Prefer typed fields, step codes, checklist item ids, photo ids, finding
   codes, and revision numbers over unstructured strings whenever practical.

2. Keep generated files secondary.

   DOCX, HTML, and PDF files are artifacts. The source of truth is:

   ```text
   report snapshot + template config + output layout + photo/evidence metadata
   ```

3. Preserve schema/version context.

   Report workflows, templates, output layouts, and rule sets must remain
   versioned so future workers can interpret old reports correctly.

4. Preserve relationships.

   Photos, findings, comments, checklists, document jobs, and worker actions
   must remain linked to office/project/site/report/step where possible.

5. Do deterministic validation before AI.

   Required fields, missing photos, status checks, permissions, and obvious
   consistency rules should be handled in code. AI should be used for semantic
   judgment, summarization, pattern recognition, wording, and ambiguity.

6. Keep office boundaries strict.

   Knowledge queries must be office-scoped by default. Cross-office queries are
   platform-admin operations only and must be explicitly designed.

7. Record AI context usage.

   When AI answers a knowledge query, ArchDox should eventually record which
   data slices were used as context, not raw hidden database access.

8. Avoid premature vector architecture.

   Start with structured PostgreSQL data and read models. Add full-text search
   or vector indexing only after real query patterns justify it.

9. Do not expose the database directly.

   Web UI, mobile apps, AI workers, document agents, external SaaS
   integrations, and future LLM workers must use stable domain APIs. They must
   not connect directly to ArchDox tables.

10. Promote concepts only when the workflow proves them.

   Some business concepts are already visible but should not be prematurely
   hardened into standalone aggregates until real office usage confirms their
   lifecycle. Keep the current domain model stable, then promote these concepts
   when needed.

## Domain Promotion Candidates

The current model is already business-oriented, not a PDF-output-only schema.
It includes office, project, site, target, report, checklist, photo, document,
agent, worker, and finding concepts.

However, these concepts are still promotion candidates:

| Candidate | Current Representation | Promote When |
| --- | --- | --- |
| `Issue` | report preflight findings, document AI findings, checklist notes, report step payloads, platform ops findings | The business needs a durable issue lifecycle across reports, sites, photos, assignees, and documents. |
| `Action` / `CorrectiveAction` | finding resolution, report step edits, operation events | Offices need to track requested action, responsible user, due date, evidence, completion, and verification. |
| `Approval` | report status, assignment roles, signature input, future workflow config | Real approval/결재 steps require submitter/reviewer/approver roles, approval history, rejection, and re-approval after revision. |
| `KnowledgeSearch` | no first-class model yet; future read models over reports/findings/photos/events | Users need repeated office-memory queries, similar case search, performance summaries, or AI context retrieval. |
| `WorkerJob` | ArchDox Worker action trace, worker chat session, operation events | Worker tasks become long-running, resumable, auditable units with their own lifecycle beyond one action envelope. |

Promotion means adding stable APIs, lifecycle states, permissions, audit events,
and read models. It does not mean exposing database tables directly.

The intended promotion rule:

```text
1. Keep the current domain model.
2. Do not expose DB tables directly.
3. Provide stable feature/domain APIs.
4. Add separate read models for search and AI context.
5. Promote Issue / Action / Approval / KnowledgeSearch / WorkerJob
   only when real workflow behavior justifies a standalone aggregate.
```

Example future API shape:

```text
GET  /api/v1/sites/{siteId}/issues
POST /api/v1/issues/{issueId}/actions
POST /api/v1/approvals
POST /api/v1/office-knowledge/search
POST /api/v1/worker-jobs/document-generation
```

These APIs should be designed around business concepts, not around the current
table layout.

## Future Worker Families

Future ArchDox Workers can use this knowledge layer without replacing the
existing UI or document workflow.

| Worker | Purpose |
| --- | --- |
| `OfficeKnowledgeSearchWorker` | Search prior reports, findings, checklists, and evidence inside one office. |
| `OfficePerformanceSummaryWorker` | Summarize office activity over a time period: projects, sites, report types, findings, document output. |
| `SimilarInspectionCaseWorker` | Find similar prior reports/sites/issues and return evidence-backed examples. |
| `RiskPatternDetectionWorker` | Detect repeated risks by site type, report type, checklist item, worker, or project. |
| `EvidenceGapAnalysisWorker` | Find reports or document types with weak/missing photo or attachment evidence. |
| `WorkerActivitySummaryWorker` | Summarize work done by assigned users without turning it into hidden employee surveillance. |
| `DocumentHistoryQaWorker` | Answer questions about generated document history and prior revisions. |
| `TemplateEffectivenessWorker` | Detect templates/layouts that frequently cause failures, edits, or missing fields. |

These workers must use the same controlled pattern:

```text
User/admin asks a question
-> Worker proposes or starts a registered action
-> Policy checks office/user scope
-> Deterministic query/read model collects candidate context
-> AI harness summarizes or reasons over the bounded context
-> Result includes citations/evidence references where possible
-> Query and AI usage are audited
```

## Search And Context Architecture

Do not send the entire office database to an AI model.

The intended growth path is:

```text
Phase 1: Structured PostgreSQL source data
  - project/site/report/photo/finding/document/operation state is stored cleanly

Phase 2: Search read models
  - denormalized report/finding/photo/search rows for fast office-scoped lookup

Phase 3: Full-text search
  - Korean-aware text search for notes, findings, report titles, descriptions

Phase 4: AI context builder
  - convert selected rows into compact, redacted, evidence-linked context

Phase 5: Optional vector index
  - only for semantic similarity when full-text/structured filters are not enough

Phase 6: Knowledge workers
  - office performance summary, similar case search, risk pattern detection
```

The AI context builder is the key boundary. It should produce small, explainable
context packets such as:

```json
{
  "officeId": 10,
  "query": "Find similar safety findings from the last two years",
  "filters": {
    "dateFrom": "2024-01-01",
    "reportTypes": ["CONSTRUCTION_DAILY_SUPERVISION_LOG"]
  },
  "evidence": [
    {
      "reportId": 101,
      "siteId": 22,
      "stepCode": "SAFETY_CHECK",
      "findingCode": "MISSING_GUARDRAIL_PHOTO",
      "summary": "Guardrail condition was noted but no supporting photo was attached."
    }
  ]
}
```

AI should answer from that packet, not from unrestricted DB access.

## Security And Privacy Rules

Knowledge features are high-value and high-risk. They must follow these rules:

- Office users can query only data they are allowed to see.
- Platform admins can run cross-office operational queries only through
  platform-admin APIs and audited operations.
- AI prompts must not include secrets, raw API keys, device secrets, physical
  NAS paths, or unnecessary personal data.
- Prompt/response raw text storage is off by default unless a specific
  debugging/consent policy enables it.
- Context packets should be logged by metadata and evidence ids, not by dumping
  all sensitive content.
- Query results should cite report/site/finding ids so users can verify answers.
- Long-term retention and deletion policies must be defined before selling this
  as office memory.

## Implementation Phases

Do not implement the full knowledge platform now. Use this roadmap when the
core document workflow is stable.

### Phase K-0: Preparation Through Current Work

Continue current work:

- keep structured reports/checklists/photos/findings
- preserve revisions and template/layout versions
- keep office/project/site/report relations intact
- record operation and worker events

No new search engine is required yet.

### Phase K-1: Office Knowledge Read Model

Create read models for:

- report searchable summary
- finding searchable summary
- photo evidence summary
- document job/artifact summary
- project/site timeline summary

This can start inside PostgreSQL.

### Phase K-2: Office Knowledge Query API

Add office-scoped read APIs:

```text
GET /api/v1/office-knowledge/reports/search
GET /api/v1/office-knowledge/findings/search
GET /api/v1/office-knowledge/sites/{siteId}/timeline
GET /api/v1/office-knowledge/projects/{projectId}/summary
```

These APIs should work without AI.

### Phase K-3: Context Builder

Add a service that turns search results into bounded AI context:

```text
OfficeKnowledgeContextBuilder
```

It should enforce:

- office/user scope
- maximum rows
- redaction
- evidence ids
- token budget

### Phase K-4: First Knowledge Worker

Start with one narrow worker:

```text
OfficePerformanceSummaryWorker
```

It should answer a bounded question such as:

```text
Summarize this office's report activity for the selected period.
```

### Phase K-5: Similar Case Search

Add:

```text
SimilarInspectionCaseWorker
```

Start with structured filters and full-text search. Add vector search only if
the results are not good enough.

### Phase K-6: Knowledge Admin And Retention

Add admin controls for:

- retention policy
- index rebuild
- AI query audit
- context export/redaction review
- office-level enable/disable

## Non-Goals For Now

Do not build these immediately:

- a generic RAG platform
- unrestricted "chat with DB"
- cross-office training corpus
- vector database before query patterns are proven
- AI answers without evidence references
- hidden employee surveillance dashboards
- direct AI access to production database

## Architecture Reminder

The long-term platform shape is:

```text
ArchDox UI
  -> ordinary workflow screens
  -> optional worker/knowledge chat

Cloud API
  -> source-of-truth domain data
  -> office-scoped read models
  -> policy and audit

ArchDox Worker
  -> controlled action/query orchestration

archdox-ai-harness
  -> bounded AI summarization/reasoning blocks

ArchDox Agent
  -> document/photo/artifact execution only
```

This keeps ArchDox from becoming a loose chatbot. The product remains a
workflow platform whose AI workers operate on verified office data.

# Site Supervision Ledger Architecture

## Decision

Construction supervision data must not belong only to a daily log document.
The daily supervision log is one way to enter and print the data. The actual
source of truth is the site supervision ledger.

In ArchDox terms:

```text
Project
-> Site
   -> Site Supervision Ledger
      -> SiteSupervisionEntry
```

Documents are projections over that ledger:

- construction daily supervision log
- trade-specific supervision checklist
- construction supervision report
- photo sheet
- issue/action report
- AI summary, search, statistics, and office knowledge workflows

## Why

The official daily log writing method says that supervision items should be
recorded based on trade-specific supervision checklists. That means the daily
log is not isolated document text. It is a field-entry surface for broader
construction supervision work.

If the same observations are stored only inside `inspection_report_steps`, later
documents would need to scrape another report's payload. That creates drift.

The ledger keeps structured observations in a stable domain table while still
preserving the original report step as the source.

## Data Ownership

`inspection_report_steps` remains the user's current document-writing draft.

`site_supervision_entries` is the shared supervision ledger.

The relationship is:

```text
DAILY_LOG step save
-> parse dailyItems
-> require report.siteId
-> validate trade/process/inspection item codes against the active construction
   supervision catalog
-> replace draft ledger entries for report/revision/step
-> keep source_report_id, source_report_revision, source_step_code

Report submit
-> mark matching ledger entries as CONFIRMED
```

This preserves both:

- document authoring history
- reusable site-level supervision facts

Current implementation rule:

- DAILY_LOG ledger projection is site-bound. If a construction daily log has no
  `siteId`, ArchDox blocks the ledger projection instead of silently skipping
  it.
- Submit validation also surfaces missing site context for workflows that use
  DAILY_LOG.
- `trade_name`, `process_name`, and `inspection_item_name` are normalized from
  the official catalog at projection time. Client payload names are readability
  hints only.
- `catalog_code` and `catalog_version` are written from the catalog service,
  not from hardcoded ledger constants.

## Current Table

```text
site_supervision_entries
```

Important columns:

- `office_id`
- `project_id`
- `site_id`
- `entry_date`
- `floor_area`
- `trade_code`, `trade_name`
- `process_code`, `process_name`
- `inspection_item_code`, `inspection_item_name`
- `supervision_content`
- `result_status`
- `issue_text`
- `action_result`
- `photo_ids`
- `status`: `DRAFT` or `CONFIRMED`
- `source_report_id`
- `source_report_revision`
- `source_step_code`
- `source_entry_key`
- `catalog_code`
- `catalog_version`

## Source Key Rule

The source key is intentionally report-revision scoped:

```text
office_id
+ source_report_id
+ source_report_revision
+ source_step_code
+ source_entry_key
```

When a draft daily log is saved again, ArchDox replaces entries for that same
report revision and step. When a report is reopened, the report content revision
increases, so the next save creates a new draft revision instead of mutating the
old confirmed revision.

Do not merge entries across revisions automatically. Auditability is more
important than clever deduplication.

## API

Current read API:

```text
GET /api/v1/projects/{projectId}/sites/{siteId}/supervision-ledger/entries
```

This is the API future document-writing screens should use when they need to
reuse site supervision observations.

Examples:

- trade-specific checklist screen loads entries for the same site and trade
- supervision report screen summarizes confirmed entries
- AI worker reviews missing evidence across entries
- office knowledge search reads site-level observations through a read model

## Relationship To Daily Log

The current daily log step payload stores:

```text
groups[]
-> floor
-> tradeCode / tradeName
-> processCode / processName
-> entries[]
   -> inspectionItemCode / inspectionItemName
   -> supervisionContent
   -> photoIds
```

This maps directly to ledger entries.

`inspectionItemCode` and `inspectionItemName` are the domain-facing names.
They represent the official checklist's `검사항목`.

The pre-production cleanup intentionally removed the old generic aliases:

- `items`
- `trade`
- `process`
- `itemCode`
- `item`
- `content`

ArchDox has not started real office production migration yet, so preserving
confusing aliases would make the domain harder to understand. If a future real
production schema must be changed, it should be versioned and migrated instead
of silently accepting multiple payload shapes forever.

The distinction is important:

```text
공종 / 세부공정
-> 검사항목
   -> 감리내용
      -> 작성자가 현장에서 확인한 세부 내용, 조치, 근거, 사진
```

The inspection item comes from the code-managed construction supervision
catalog. The supervision content is the user's authored field note for that
inspection item.

The daily log document renderer may still read report snapshot fields directly
for now. Longer term, document snapshots should include selected ledger entries
so multiple documents can be generated from the same supervision source data.

## Future Extensions

The next natural upgrades are:

0. Move ledger projection from direct service invocation toward a Bloom-backed
   domain event projection once more projections depend on report step saves.
   The current inline projection is acceptable for the MVP because it keeps the
   report draft and ledger rows transactionally aligned.
1. Add ledger selection APIs for checklist/report generation:
   - by date range
   - by trade/process/item
   - by status
   - by source report
2. Add checklist answer linkage:
   - inspection/checklist item code
   - answer/result
   - evidence policy
3. Add issue/action domain promotion:
   - `issue_text`
   - `action_result`
   - responsible member
   - due date
   - closure status
4. Add ledger-backed document snapshots:
   - selected entry IDs
   - selected layout revision
   - generated artifact history
5. Add AI/knowledge read models:
   - semantic search
   - office performance summary
   - repeated issue discovery

## Non-Goals

Do not build a giant generic workflow DSL here.

Do not make React own trade/process/check item lists.

Do not make one document type mutate another document type directly. Documents
should share the ledger, not scrape each other.

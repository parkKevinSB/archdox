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
-> replace draft ledger entries for report/revision/step
-> keep source_report_id, source_report_revision, source_step_code

Report submit
-> mark matching ledger entries as CONFIRMED
```

This preserves both:

- document authoring history
- reusable site-level supervision facts

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
- `item_code`, `item_name`
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
-> tradeCode / trade
-> processCode / process
-> items[]
   -> itemCode / item
   -> content
   -> photoIds
```

This maps directly to ledger entries.

The daily log document renderer may still read report snapshot fields directly
for now. Longer term, document snapshots should include selected ledger entries
so multiple documents can be generated from the same supervision source data.

## Future Extensions

The next natural upgrades are:

1. Add ledger selection APIs for checklist/report generation:
   - by date range
   - by trade/process/item
   - by status
   - by source report
2. Add checklist answer linkage:
   - checklist item code
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

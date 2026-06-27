# Construction Supervision Domain Catalog

## Decision

ArchDox must not treat construction supervision forms as a few React dropdowns.
The supervision vocabulary is business domain knowledge and must be versioned
as ArchDox-owned catalog data.

The active MVP business scope is construction supervision only. Demolition
supervision and demolition safety documents are deferred to a later, separate
domain phase. They must not be mixed into the construction supervision
workflow, document type list, template field catalog, or UI selection flow until
the construction supervision workflow is stable.

The source of truth for a generated document is:

```text
report snapshot
+ workflow revision
+ domain catalog version
+ template/layout revision
```

Generated DOCX/PDF/HTML/HWP artifacts are outputs. They are not the business
source of truth.

## Why This Matters

Construction daily supervision logs, phase checklists, and trade-specific
checklists share the same vocabulary:

- floor or work area
- trade
- detailed process
- inspection item (`검사항목`)
- narrative supervision content
- linked photos
- issue/action result

If these values live only inside React components, later changes become
dangerous:

- legal form revisions cannot be tracked
- office-specific overrides become hard-coded
- AI review cannot reason against stable item codes
- old reports cannot be regenerated against a different layout
- checklist documents and daily logs drift apart

## Current Catalog

The first code-managed system catalog is:

```text
CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24
```

Resource:

```text
cloud-api/src/main/resources/domain-catalogs/construction-supervision-checklist-2020-12-24.json
```

Reference:

```text
docs/reference-forms/korean/[별표 1] 단계별 감리 체크리스트 대장(건축공사 감리세부기준).pdf
```

API:

```text
GET /api/v1/supervision-domain-catalogs/{catalogCode}
```

The React daily supervision step reads this catalog from Cloud API. It must not
own the trade/check item list.

## Domain Asset Lifecycle

Construction supervision catalog data is a domain asset, not a UI constant.
ArchDox should manage it like versioned master/reference data.

Current MVP lifecycle:

```text
code-managed JSON catalog
-> Cloud API reads and caches it from resources
-> client UI consumes it through API
-> report payload stores selected codes and names
-> site supervision ledger validates selected codes against the catalog
-> site supervision ledger stores official codes, official names, catalog code,
   catalog version, and source references
```

This is intentionally simple while the construction supervision vocabulary is
still being refined. The JSON file is easy to review in Git, test in CI, and
change while the product shape is still moving.

Current implementation rule:

- The JSON catalog's `version` field is the source of truth for the MVP
  catalog version stamp.
- The Cloud API caches the classpath JSON catalog in memory and returns copies
  to callers.
- The daily log UI may send display names for user readability, but ledger
  persistence must not trust those display names.
- On DAILY_LOG save, Cloud API resolves `tradeCode`, `processCode`, and
  `inspectionItemCode` through `SupervisionDomainCatalogService`.
- Unknown or missing catalog codes are rejected before ledger rows are written.
- Ledger rows store the official catalog names from the catalog, not arbitrary
  client-provided names.

Target production lifecycle:

```text
code-managed JSON seed/source
-> imported or published into DB catalog revisions
-> Cloud API reads active DB revision
-> office-specific overrides are stored as separate DB revisions
-> report/ledger data snapshots the selected catalog code and version
```

The future DB model should be shaped roughly as:

```text
domain_catalogs
domain_catalog_revisions
office_catalog_overrides
```

The code JSON remains useful as the system seed and reviewable source material,
but runtime catalog resolution should eventually come from immutable published
DB revisions. This allows admin-managed publication, office overrides, effective
dates, deprecation, and audit history without changing application code.

Do not jump straight to editable DB catalogs before the base structure is
stable. Premature admin editing would make it harder to understand whether a
problem comes from the official reference, the data model, an office override,
or the UI.

## Catalog Shape

The catalog is intentionally hierarchical. The row-level checklist model is the
canonical direction for construction daily supervision data:

```text
Catalog
-> Trade
   -> ProcessGroup
      -> workCategory: BASIC or BASIC_OUTSIDE
      -> InspectionItem
         -> ChecklistRow
```

Example:

Canonical row-level example:

```json
{
  "code": "REINFORCED_CONCRETE",
  "name": "철근 콘크리트 공사",
  "discipline": "ARCHITECTURE",
  "processGroups": [
    {
      "code": "REBAR_ASSEMBLY",
      "name": "철근 조립·배근",
      "workCategory": "BASIC",
      "workCategoryName": "기본 업무",
      "items": [
        {
          "code": "RC_REBAR_CONFIRMATION",
          "name": "철근배근의 확인사항",
          "checklistRows": [
            {
              "code": "RC_REBAR_COUNT_DIAMETER_PITCH",
              "label": "개수, 철근지름, 피치 확인",
              "basis": "개수, 철근지름, 피치 확인"
            }
          ]
        }
      ]
    }
  ]
}
```

The simplified item-level example below is historical and must not override the
row-level model above.

```json
{
  "code": "REINFORCED_CONCRETE",
  "name": "철근 콘크리트 공사",
  "discipline": "ARCHITECTURE",
  "processGroups": [
    {
      "code": "REBAR_ASSEMBLY",
      "name": "철근 조립·배근",
      "items": [
        {
          "code": "RC_REBAR_COUNT_DIAMETER_PITCH",
          "name": "철근 개수·지름·피치",
          "basis": "철근 개수, 지름, 피치 확인"
        }
      ]
    }
  ]
}
```

The top-level `trades[].items` remains as a catalog convenience summary for UI
lookup. The richer catalog structure is `trades[].processGroups[].items`.

When an official inspection item contains multiple concrete supervision
contents, those contents must be represented as `checklistRows`. The user's row
answers, result, reference note, and action note are the canonical business
data. `supervisionContent` is generated or authored prose for the daily log and
must not be the only source of truth.

## Current Coverage

Catalog version 2 covers the reference PDF pages 22-73 at this level:

- 46 trade records
- all trade/detail-process headers from the reference pages
- 51 current process groups after the reinforced-concrete row-level refinement
- 240 seeded supervision/check items

This is not yet the final row-by-row extraction of every checklist line. Most
trades are still broad seeded items. Reinforced concrete is the first trade
partially converted to the row-level model. The current row audit is tracked in:

```text
docs/development/CONSTRUCTION_SUPERVISION_CHECKLIST_ROW_AUDIT.md
```

## Daily Log Payload

The daily supervision step stores neutral structured data:

Canonical row-level payload:

```json
{
  "groups": [
    {
      "tradeCode": "REINFORCED_CONCRETE",
      "tradeName": "철근 콘크리트 공사",
      "processCode": "REBAR_ASSEMBLY",
      "processName": "철근 조립·배근",
      "workCategory": "BASIC",
      "workCategoryName": "기본 업무",
      "floor": "기초층",
      "entries": [
        {
          "inspectionItemCode": "RC_REBAR_CONFIRMATION",
          "inspectionItemName": "철근배근의 확인사항",
          "checklistRows": [
            {
              "code": "RC_REBAR_COUNT_DIAMETER_PITCH",
              "label": "개수, 철근지름, 피치 확인",
              "result": "COMPLIANT",
              "referenceNote": "도면 및 현장 배근 상태 확인",
              "actionNote": "",
              "photoIds": [10, 11]
            }
          ],
          "supervisionContent": "개수, 철근지름, 피치 확인 등 철근배근의 확인사항을 점검했습니다."
        }
      ]
    }
  ]
}
```

Codes are for stable traceability. Korean names are stored with the snapshot so
reports remain readable even if catalog wording later changes.

`entries[]`, `tradeName`, `processName`, `inspectionItemCode`,
`inspectionItemName`, `checklistRows[]`, and `supervisionContent` are the
canonical field names. For row-level trades, `inspectionItemCode` identifies the
official inspection item and `checklistRows[]` identifies the concrete
supervision contents under that item.
Earlier generic aliases such as `items`, `trade`, `process`, `itemCode`,
`item`, and `content` were removed during the pre-production cleanup. This is
intentional: ArchDox is not yet in a real office data migration phase, so the
domain schema should stay clear rather than preserve confusing compatibility.

The catalog item is the official `검사항목`; the user's
`checklistRows[]` are the structured field-authored `감리내용` units attached to
that inspection item. `supervisionContent` is derived prose for rendering and
review context.

In the row-level model, the catalog inspection item is the official 검사항목.
Checklist rows are the official or ArchDox-normalized 감리내용 units under that
검사항목. Row-level `result`, `referenceNote`, `actionNote`, and `photoIds` are
the source data.

Generated daily-log prose should avoid repeating the inspection item when the
inspection item is also represented as a parent checklist row. If that parent
row has an inspected result (`COMPLIANT` or `NON_COMPLIANT`), render it as the
parent line and render selected child rows below it. If the parent row is
`NOT_APPLICABLE` or otherwise unselected, do not repeat the inspection item text
in the generated content; render only selected child row lines. A
`documentNarrativeText` override remains stronger than generated prose.

## Domain Asset Strategy

The current catalog is code-managed so the team can quickly reshape the
construction-supervision taxonomy while the product is still being validated.
This does not mean React owns the domain data. React only renders API data.

Longer term, the catalog should be promoted to managed domain assets:

- `domain_catalogs`
- `domain_catalog_revisions`
- `supervision_trades`
- `supervision_process_groups`
- `supervision_inspection_items`
- effective date, source document, revision label, active/published status

Until real offices start relying on production data, breaking catalog and
payload changes are allowed when they simplify the canonical model. After real
operation starts, changes must be versioned and migrated.

## Layout Versioning

Document layout is a separate concern.

For example, the bundled official construction daily log currently uses:

```text
templateCode: KOREAN_CONSTRUCTION_DAILY_SUPERVISION_LOG_APPENDIX_2
formRevision: 건축공사 감리세부기준 별지 제2호서식 개정 2017. 2. 4.
layoutVersion: 1
```

If the legal form changes, ArchDox should add a new template/layout revision.
Existing reports can still be regenerated with either the old layout or a newer
layout because the stored report data is neutral.

## Extension Rule

Additions should happen in this order:

1. Extend the code-managed system catalog from the reference PDF.
2. Add tests that verify the catalog returns stable codes and expected coverage.
3. Let UI consume the catalog through API.
4. Add office-level catalog overrides only after the base catalog is stable.
5. Connect checklist answers and daily log entries to the same item codes.
6. Extract the remaining reference PDF rows into process-group item records
   after the current structure stabilizes.

Do not add new construction supervision trade/check lists directly inside React
components.

## Change Safety

The current architecture is designed so the catalog can evolve without tangling
the whole system:

- React does not own the construction supervision list; it reads the catalog
  through Cloud API.
- Report steps store selected catalog codes and display names in
  `payload_json`, so old drafts remain readable even if catalog wording later
  changes.
- `site_supervision_entries` stores the selected trade/process/inspection item
  codes, display names, `catalog_code`, and `catalog_version`.
- Ledger rows also keep `source_report_id`, `source_report_revision`,
  `source_step_code`, and `source_entry_key`, so their origin remains traceable.
- Document layouts are separate template/layout revisions, not embedded inside
  the catalog.

This means ArchDox can change the catalog shape, but not recklessly. The safe
rule is:

```text
small label/basis improvement
-> keep stable codes and increment catalog version if behavior changes

new official form or major structure change
-> create a new catalog code or published catalog revision

field rename in payload
-> keep old aliases until old reports and renderers no longer need them
```

So the current structure is not locked forever. It is intentionally staged:
code JSON now, DB-published immutable revisions later. Existing report and
ledger data should continue to be interpreted through their saved
`catalog_code`, `catalog_version`, codes, and snapshot names.

## Future Upgrade

When the full row-level extraction is needed, do not replace this model. Expand
it:

```text
Trade
-> ProcessGroup
   -> ChecklistSection
      -> Item
         -> requirement/basis
         -> evidence policy
         -> default answer schema
```

This keeps the current daily log UI useful while preserving a path toward full
construction supervision checklist authoring, AI review, statistics, and
office-specific policy overrides.

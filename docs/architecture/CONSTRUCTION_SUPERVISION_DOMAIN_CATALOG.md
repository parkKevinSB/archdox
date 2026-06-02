# Construction Supervision Domain Catalog

## Decision

ArchDox must not treat construction supervision forms as a few UI dropdowns.
The supervision vocabulary is domain knowledge and must be versioned as
ArchDox-owned catalog data.

The source of truth for a report is:

```text
report snapshot
+ workflow revision
+ domain catalog version
+ template/layout revision
```

Generated DOCX/PDF/HTML/HWP artifacts are outputs, not business data.

## Why This Matters

Construction daily supervision logs, phase checklists, and trade-specific
checklists share the same vocabulary:

- floor or work area
- trade
- detailed process
- supervision/check item
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

The first code-managed catalog is:

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

The React daily supervision step reads this catalog from Cloud API. It should
not own the trade/check item list.

## Data Shape

Daily supervision step payload stores structured neutral data:

```json
{
  "groups": [
    {
      "tradeCode": "REINFORCED_CONCRETE",
      "trade": "철근 콘크리트 공사",
      "processCode": null,
      "process": "기초, 지하층 바닥",
      "floor": "기초층",
      "items": [
        {
          "itemCode": "REBAR_ASSEMBLY",
          "item": "철근 조립, 배근",
          "content": "현장 작성자가 확인한 감리내용",
          "photoIds": [10, 11]
        }
      ]
    }
  ]
}
```

Codes are for stable traceability. Korean names are stored with the snapshot so
old reports remain readable even if catalog wording later changes.

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
2. Add tests that verify the catalog returns stable codes.
3. Let UI consume the catalog through API.
4. Add office-level catalog overrides only after the base catalog is stable.
5. Connect checklist answers and daily log entries to the same item codes.

Do not add new construction supervision trade/check lists directly inside React
components.

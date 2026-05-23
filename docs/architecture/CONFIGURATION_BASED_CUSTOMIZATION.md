# Configuration-Based Customization

ArchDox must absorb customer-specific differences without turning the codebase
into office-specific branches.

The target is not "everything is a DSL." The target is:

```text
stable engine code + versioned templates/configuration + tenant-safe overrides
```

## Problem To Avoid

Do not implement office-specific or report-type-specific behavior like this:

```java
if (officeId.equals("A")) {
    // A office layout
} else if (officeId.equals("B")) {
    // B office layout
}
```

This pattern grows into an untestable product variant matrix. It also makes
deployment necessary for routine business changes such as template wording,
photo layout, required fields, approval stages, or storage policy.

## Design Goal

Developers should add engine capabilities, not hard-code individual customer
procedures.

Customer differences should usually be represented by:

- document templates
- output layout configuration
- workflow definitions
- validation/rule sets
- storage and delivery policies
- office-level overrides
- project/site/target-aware report schemas

The code should resolve a configuration, validate it, snapshot it when needed,
and pass it to a stable engine or Flower flow.

## Customization Layers

### 1. Document Template

A DOCX template may contain placeholders such as:

```text
${projectName}
${inspectorName}
${inspectionDate}
${photoSection}
```

The engine should bind report data into the selected template. It should not
know that a specific office calls the same field `PROJECT` while another office
calls it `SITE NAME`.

Template rules:

- templates are versioned
- system templates can be shared across offices
- office-specific templates override system defaults
- existing reports and jobs must keep the template revision selected at the
  time of creation or generation

### 2. Output Layout Configuration

Output layout configuration describes repeated layout choices such as photo
tables, section order, field labels, and optional blocks.

Example:

```json
{
  "sections": [
    {
      "type": "photoTable",
      "repeat": "photos",
      "columns": ["photo", "description", "location"],
      "photosPerRow": 2
    }
  ]
}
```

This is a small domain-specific configuration, not a general programming
language. It should express supported document layout choices only.

### 3. Workflow Definition

Workflow definitions describe which business stages are required for a report or
document process.

Example:

```json
{
  "workflow": [
    "PHOTO_UPLOAD",
    "REVIEW",
    "APPROVAL",
    "PDF_GENERATE",
    "NAS_STORE"
  ]
}
```

Flower remains the runtime orchestration engine. A workflow definition may choose
or parameterize supported stages, but it must not execute arbitrary code.

Workflow rules:

- Flower owns waiting, retry, timeout, and backoff
- stage names must map to known, tested step handlers or flow routes
- workflow definitions are versioned
- in-progress workflows should keep their selected workflow revision
- changing a workflow definition should not mutate historical workflow meaning

For report-writing UI, workflow configuration is intentionally narrower than
backend Flower orchestration. It can describe supported client steps:

```json
{
  "flowId": "daily-supervision-v1",
  "title": "감리일지 작성",
  "steps": [
    {
      "code": "BASIC_INFO",
      "title": "기본 정보",
      "description": "공통 머리말 정보를 입력합니다.",
      "stepType": "FORM",
      "fields": [
        {
          "key": "inspectionDate",
          "label": "점검일",
          "type": "date",
          "required": true
        }
      ]
    }
  ]
}
```

This is not arbitrary UI JSON. `stepType` must map to known React step
components such as `FORM`, `CHECKLIST`, or `PHOTO`. If configuration is missing
or invalid, the client receives a built-in default flow and the admin can fix
the published revision separately.

### 4. Rule Set

Rule sets describe configurable business requirements.

Examples:

```json
{
  "reportType": "SAFETY_CHECK",
  "minPhotos": 3,
  "requiredFields": ["maskCheck", "airMeasurement"]
}
```

Rule sets are appropriate for:

- required fields
- minimum photo counts
- required approval roles
- report-type-specific checklist requirements
- plan/office limits that are business policy rather than infrastructure limits

Do not move core security, tenant isolation, or data ownership invariants into
JSON rules. Those remain code-level guarantees.

Initial submit-validation rule support:

```json
{
  "minWorkingPhotos": 2,
  "requiredSteps": ["BASIC_INFO"]
}
```

`minWorkingPhotos` and `minPhotos` control the minimum uploaded working-image
asset count when the resolved report workflow contains a `PHOTO` step.
`requiredSteps` is a small escape hatch for requiring a known saved step while
the workflow schema is still young. Field-level requirements should preferably
live in workflow step `fields[].required`.

### 5. Office Override

Office-specific behavior should be expressed as an override that points to a
specific template, workflow, rule set, output layout, or storage policy.

Resolution order:

1. office-specific active override
2. plan/report-type default
3. system default

Every resolution result should be explicit enough to debug:

- selected config id
- selected revision/version
- source: `OFFICE_OVERRIDE`, `PLAN_DEFAULT`, or `SYSTEM_DEFAULT`
- report type and office id used for resolution
- site/target type used for resolution when that dimension is supported

Future-compatible resolution dimensions:

```text
officeId
planCode
reportType
siteType
targetType
templateCode/workflowCode
```

MVP resolution can remain `officeId + reportType`, but the schema and code
should not assume that report type alone fully describes the physical work
context. For example, `SAFETY_CHECK + BUILDING` and
`SAFETY_CHECK + EQUIPMENT` may need different fields, rules, and output layout.

## Versioning And Snapshot Rules

Configuration must be versioned because generated documents need to be
explainable later.

When creating or generating a document, store a snapshot or revision reference
for:

- template revision
- output layout revision
- workflow definition revision
- rule set revision
- relevant storage/delivery policy

Do not re-resolve the "latest" config when rendering an already-created job
unless the user explicitly requests regeneration with a new configuration.

## Database Direction

The future persistence model should be built around these concepts:

- `document_templates`
- `document_template_revisions`
- `workflow_definitions`
- `workflow_definition_revisions`
- `rule_sets`
- `rule_set_revisions`
- `output_layout_configs`
- `output_layout_config_revisions`
- `office_config_overrides`

Ownership rules:

- system default rows may use `office_id NULL`
- office custom rows use `office_id NOT NULL`
- revision rows should be immutable after publication
- uniqueness must include tenant scope where applicable
- published config rows should have lifecycle states such as `DRAFT`,
  `PUBLISHED`, `ARCHIVED`

## What Should Not Be Configured Yet

Avoid premature DSL expansion.

Do not config-drive these until a real repeated pattern appears:

- arbitrary Java service calls
- security and authorization rules
- tenant isolation
- low-level storage adapter internals
- raw SQL or unrestricted query behavior
- full programming expressions inside JSON

Start with narrow configuration for the parts that customers really change:

- template file
- field labels
- section order
- photo layout
- required fields
- approval stages
- output format and delivery target

## Implementation Phases

### Phase A: Documentation And Rules

Record the no-hardcoded-office rule and the target configuration model.

### Phase B: Configuration Registry Foundation

Add DB tables and domain/read APIs for versioned templates, workflow
definitions, rule sets, output layouts, and office overrides.

This phase can be mostly metadata. It does not need a full UI editor yet.

Implemented foundation:

- DB tables for document templates, workflow definitions, rule sets, output
  layout configs, their revisions, and office overrides
- office admin APIs under `/api/v1/config`
- create/list revision flows
- explicit publish endpoints
- office override assignment by `reportType`
- resolve API that returns the selected revision and source for template,
  workflow, rule set, and output layout

Current limitation:

- office admin APIs create office-owned configuration only
- system default configuration rows are supported by the schema/resolver, but
  need future platform-admin or seed tooling
- document generation does not yet consume resolved configuration

### Phase C: Template Binding First

Wire document generation to resolve a template revision and bind data through
`document-engine`.

Implemented first binding:

- document job creation resolves configuration by `officeId + reportType`
- selected template/workflow/rule/layout revisions are snapshotted into
  `document_jobs.input_snapshot_json.configuration`
- selected template revision id is stored in `document_jobs.template_id` as the
  current compatibility field
- Cloud document generation builds `TemplateSpec` from the snapshot, not by
  re-resolving latest config later
- fallback remains `templates/default.docx` when no configuration exists
- document job snapshots now include `project`, `site`, and `templateFields`
- template revision `schema.bindings` maps DOCX-friendly placeholder names to
  snapshot paths, for example:

```json
{
  "bindings": {
    "projectName": "project.name",
    "siteName": "site.name",
    "inspectionDate": "steps.BASIC_INFO.payload.inspectionDate",
    "weather": "steps.BASIC_INFO.payload.weather"
  }
}
```

Current capability:

- `DocxTemplateDocumentEngine` can read a configured DOCX template and replace
  placeholders that appear as intact text inside Word XML, such as
  `${report.title}`, `${projectName}`, `${siteName}`, `${weather}`,
  `${templateCode}`, and `${templateVersion}`
- DOCX Placeholder Hardening V1 also supports placeholders split across
  multiple Word text nodes, for example `${pro` + `ject` + `Name}`. The engine
  writes the resolved value into the first involved text node and clears the
  remaining placeholder fragments, preserving the surrounding Word run
  structure.
- missing template files fall back to the simple generated DOCX path, preserving
  the MVP behavior
- unresolved placeholders remain visible in the output so missing data is easier
  to catch
- office admins can upload a DOCX file to a `DRAFT` document template revision
  through `PUT /api/v1/config/document-template-revisions/{revisionId}/content`
- uploaded template files are stored by the server under document object storage
  and the revision receives `templateStorageKind = API_LOCAL` plus a generated
  `templateStorageRef`
- template content can be downloaded through
  `GET /api/v1/config/document-template-revisions/{revisionId}/content`
- after publication, template revision content is immutable; changes require a
  new revision
- `ARCHDOX_AGENT` document rendering uses the same `document-engine` template
  binding code as Cloud rendering. Cloud includes an agent-authenticated
  template download URL in the `GENERATE_DOCUMENT` command, and the Agent may
  cache immutable template revision content under its configured template
  storage root.

Remaining limitations:

- DOCX `PHOTO_TABLE` insertion supports a generated Word table with working
  image media entries when image content can be resolved by Cloud or ArchDox
  Agent storage. More advanced table styling, image sizing from actual
  dimensions, captions, and multi-column photo grids remain later refinements.
- admin UI supports template definition/revision/upload/publish/override
  management; a rich visual template/schema editor remains later
- complex Word fields, content controls, headers/footers with unusual XML
  structures, and arbitrary table/image DSL features remain later hardening
  phases

### Phase D: Output Layout V1

Support a small, explicit output layout config for photo sections and simple
field/section ordering.

Implemented first layout binding:

- selected output layout revision is snapshotted under
  `document_jobs.input_snapshot_json.configuration.outputLayout`
- output layout `payload.sections` creates `layoutSections`
- each section writes a text block into `templateFields` by section `key`
- `PHOTO_TABLE` can also replace a matching DOCX paragraph placeholder with a
  generated Word table and embedded image media entries
- `PHOTO_TABLE` supports narrow layout options:
  - `photosPerRow`: `1`, `2`, or `3`; default `1`
  - `imageSize`: `THUMBNAIL`, `MEDIUM`, or `ORIGINAL`; default `MEDIUM`
  - `fields`: metadata rows to show under or beside each photo
- `CHECKLIST_TABLE` can also replace a matching DOCX paragraph placeholder with
  a generated Word table from saved checklist answers
- supported section types:
  - `PHOTO_SUMMARY`, `PHOTO_LIST`: text summary from uploaded photo snapshot
    rows
  - `PHOTO_TABLE`: text summary plus DOCX table/image insertion when the
    template uses `${sectionKey}` as a standalone paragraph placeholder
  - `CHECKLIST_SUMMARY`, `CHECKLIST_LIST`: text summary from checklist answer
    snapshot rows
  - `CHECKLIST_TABLE`: text summary plus DOCX table insertion when the template
    uses `${sectionKey}` as a standalone paragraph placeholder
  - `VALUE`, `FIELD`, `SNAPSHOT_VALUE`: single value read from the document job
    snapshot path
  - `TEXT`: literal text block

Example:

```json
{
  "sections": [
      {
        "key": "photoSection",
        "type": "PHOTO_TABLE",
        "title": "Photo Section",
        "photosPerRow": 2,
        "imageSize": "THUMBNAIL",
        "fields": [
          {
            "label": "Caption",
            "source": "caption"
          }
        ]
      },
    {
      "key": "checklistSection",
      "type": "CHECKLIST_TABLE",
      "title": "Checklist Section",
      "fields": [
        {
          "label": "Item",
          "source": "itemCode"
        },
        {
          "label": "Result",
          "source": "answer.value"
        },
        {
          "label": "Note",
          "source": "note"
        }
      ]
    }
  ]
}
```

With this layout, DOCX can use `${photoSection}` and `${checklistSection}`.
`PHOTO_TABLE` and `CHECKLIST_TABLE` are rich layouts: when the section
placeholder is a standalone paragraph, `document-engine` replaces it with a
Word table. `PHOTO_TABLE` embeds resolvable working images into the DOCX
package. `CHECKLIST_TABLE` uses the saved checklist answer snapshot and supports
field sources such as `itemCode`, `label`, `answer.value`, and `note`.

`photosPerRow = 1` renders a detail table with an image column and a metadata
column. `photosPerRow = 2` or `3` renders a photo grid where each cell contains
the image and configured metadata fields. This remains intentionally narrow:
it is enough for common inspection/photo-report layouts without turning output
layout JSON into a general document programming language.

### Phase E: Workflow Definition V1

Allow report/document workflows to select from supported stage sets while still
executing through Flower.

### Phase F: Admin Editing

Expose safe admin screens for template upload, config revision publication, and
office override assignment.

## Development Rule

If new code wants to branch on `officeId`, stop and ask:

```text
Is this really tenant isolation, or is it a customer-specific business variant?
```

Tenant isolation belongs in code. Customer-specific business variants usually
belong in templates, workflow definitions, rule sets, output layouts, or office
overrides.

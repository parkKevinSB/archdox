# Document Neutral Model

ArchDox must treat the document source of truth as a neutral business snapshot,
not as DOCX, PDF, HWP, HTML, or any customer-specific template.

This is a core architecture rule. ArchDox is a document workflow platform, so
document generation must begin from stable workflow data and versioned
configuration.

## Core Rule

```text
business input
-> neutral document snapshot
-> template bindings + output layout + rules
-> DOCX / HTML / PDF / HWPX / HWP artifacts
```

The neutral snapshot is the contract between Cloud API, ArchDox Agent,
document-engine, future document-worker processes, AI review modules, and admin
configuration.

## Current V1 Shape

`document_jobs.input_snapshot_json` is the V1 neutral document model.

It should contain document-generation data such as:

- `report`
- `project`
- `site`
- `targets`
- `steps`
- `photos`
- `checklistAnswers`
- `configuration`
- `templateFields`
- `layoutSections`

Renderers and exporters may read this snapshot, but they must not mutate it or
replace it as the source of truth.

## Format Boundary

Output formats are artifact targets.

- `DOCX` is the first editable/renderable substrate.
- `HTML` is a browser preview artifact.
- `PDF` is a delivery artifact produced by a converter such as LibreOffice.
- `HWP` and `HWPX` are Korean delivery/export targets to be added behind
  renderer/exporter interfaces.

No business rule should depend on a physical artifact format. For example, a
required checklist, approval flow, photo count, site type, or template binding
belongs in workflow/rule/template/output-layout configuration and then in the
snapshot.

## Template Binding

Templates should use business-friendly fields, not internal Java or database
structure.

Example:

```json
{
  "bindings": {
    "projectName": "project.name",
    "siteName": "site.name",
    "inspectionDate": "steps.BASIC_INFO.payload.inspectionDate",
    "inspectorName": "steps.BASIC_INFO.payload.inspectorName"
  }
}
```

DOCX may then use:

```text
${projectName}
${siteName}
${inspectionDate}
${inspectorName}
```

The same `templateFields` can be used by HTML preview, PDF conversion, AI review,
and future HWPX rendering.

## Development Rules

1. Do not add office-specific document generation branches such as
   `if officeId == A`.
2. Do not make DOCX XML, HTML markup, PDF files, or HWP files the business data
   source of truth.
3. Add new business data to the neutral snapshot first, then bind it into
   templates/layouts.
4. Add new layout behavior through bounded output-layout section types, not a
   general-purpose scripting language.
5. Add new artifact formats behind `document-engine` renderer/exporter
   interfaces.
6. Keep generated artifacts immutable for their `reportRevision` and
   `input_snapshot_json`.
7. When a report is edited after generation, create a new content revision and
   generate a new document job rather than rewriting the old artifact.

## Refactoring Direction

The current V1 implementation is intentionally practical and uses
`Map<String, Object>` snapshots. As the model stabilizes, split the current
responsibilities into clearer components:

- `DocumentSnapshotBuilder`: creates the neutral snapshot from report/project/
  site/target/photo/checklist/configuration data.
- `TemplateBindingResolver`: resolves template schema bindings into
  `templateFields`.
- `OutputLayoutCompiler`: turns versioned output-layout configuration into
  neutral `layoutSections`.
- `DocumentGenerationRequest`: remains the document-engine input contract.

This refactoring should be incremental. Do not introduce a broad generic DSL or
large abstraction before repeated customer variation proves the need.

Implemented foundation:

- `cloud-api` now builds `document_jobs.input_snapshot_json` through
  `DocumentSnapshotBuilder`.
- Template field resolution is isolated in `TemplateBindingResolver`.
- Output layout section compilation is isolated in `OutputLayoutCompiler`.
- `DocumentJobService` remains responsible for job lifecycle, routing, progress,
  and artifact metadata rather than low-level snapshot assembly.

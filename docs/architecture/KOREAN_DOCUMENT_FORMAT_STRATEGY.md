# Korean Document Format Strategy

ArchDox serves Korean construction and inspection workflows, so HWP/HWPX and
PDF delivery matter. The internal document engine should still keep a stable,
testable rendering substrate instead of making HWP the first core engine.

## Current Decision

Use DOCX as the MVP rendering substrate, then export to HTML/PDF/HWP as
separate artifact steps.

```text
Report snapshot
Template fields
Output layout sections
Photo/checklist assets
        |
        v
DOCX template renderer
        |
        +--> DOCX artifact
        +--> HTML exporter
        +--> PDF exporter
        +--> HWP/HWPX exporter
```

This does not mean Korean customers must use DOCX as the final business format.
It means DOCX is the first controllable intermediate artifact for generation,
testing, layout iteration, and conversion.

## Why DOCX First

- DOCX is an open ZIP/XML format, so the engine can bind placeholders, inspect
  generated XML, embed images, and test output in CI.
- The same `document-engine` code can run in Cloud Agent and ArchDox Agent.
- Template binding can be tested without installing office software.
- Output layout configuration can be expressed as explicit sections such as
  `PHOTO_TABLE` and `CHECKLIST_TABLE`.
- Generated DOCX can become the source artifact for HTML, PDF, or HWP/HWPX
  export.

## HTML Strategy

HTML is a strong future format for preview, web-native documents, AI-assisted
explanatory reports, and HTML-to-PDF conversion. It should be supported as a
first-class artifact type, but not by mixing web rendering code into the DOCX
renderer.

Recommended stages:

1. Add `HTML` as an artifact/export target.
2. Initially allow `DOCX -> HTML` or snapshot-driven `HtmlTemplateRenderer`
   implementations behind `DocumentArtifactExporter` or a renderer facade.
3. Use HTML for browser preview and potentially for `HTML -> PDF` pipelines
   where layout requirements fit web rendering better than Word layout.

HTML must follow the same rule as HWP/PDF: the business source of truth is the
report snapshot and versioned configuration, not the generated HTML file.

## Why Not HWP First

HWP is important as a Korean delivery format, but binary HWP generation is a
poor first engine target.

- Legacy `.hwp` is a proprietary compound binary format.
- Programmatic table/image editing is harder to test and debug than DOCX XML.
- Server-side HWP conversion depends on local office software, commercial SDKs,
  or environment-specific converters.
- Early direct HWP rendering would slow down the workflow engine before the
  real field/report model is stable.

HWP/HWPX support should be added as an export/conversion capability first. A
native HWP/HWPX renderer can be considered later if real customer templates
require it and a reliable library or SDK is selected.

## PDF Strategy

PDF is a delivery/export artifact, not the primary authoring model.

Recommended first implementation:

```text
DOCX render
-> LibreOffice or compatible headless converter
-> PDF artifact
```

The converter should live behind an interface such as `PdfExportService` or
`DocumentArtifactExporter`, with implementations selected by environment:

- local/dev: disabled or local LibreOffice
- office agent: local LibreOffice/Hancom-capable converter if installed
- cloud agent: containerized LibreOffice or managed converter

PDF conversion must preserve fonts, page breaks, Korean text, images, and table
layout. This requires deployment-level font management.

## HWP/HWPX Strategy

Recommended stages:

1. DOCX is generated and stored as the canonical editable artifact.
2. HWP/HWPX export is added behind a converter interface.
3. The converter can run inside ArchDox Agent where Korean office tools may be
   installed on the office PC.
4. If stable HWPX generation becomes available, add a native `HwpxRenderer`
   without changing the report snapshot or output layout model.

Do not store HWP-specific physical paths or converter assumptions in business
tables. Store logical artifact metadata:

```text
artifactId
artifactType
storageType
storageRef
contentType
bytes
sha256
sourceArtifactId
```

## Renderer Layer Direction

Keep the render layers separated:

```text
DocumentGenerationService
  - job lifecycle
  - worker selection
  - snapshot preparation
  - Flower flow submission

DocumentRenderEngine
  - common render entrypoint
  - validates requested output family
  - delegates to a renderer/exporter

DocxTemplateRenderer
  - DOCX template loading
  - placeholder binding
  - rich table generation
  - image media embedding

DocumentArtifactExporter
  - HTML export
  - PDF export
  - HWP/HWPX export
  - converter-specific execution

ArtifactStorage
  - stores generated files
  - returns logical storage references
```

The report snapshot, template field bindings, output layout config, photo
assets, and checklist answers must remain format-neutral. Only the final
renderer/exporter should know about DOCX, HTML, PDF, HWP, or HWPX mechanics.

## Implemented Foundation

`document-engine` now has an explicit export layer:

- `DocumentArtifactExporter`
- `DocumentArtifactExportService`
- `DocumentExportRequest`
- `DocumentExportResult`

Supported artifact type names are:

- `DOCX`
- `HTML`
- `PDF`
- `HWP`
- `HWPX`
- `PRINT_LOG`

Supported output format requests are:

- `DOCX`
- `HTML`
- `HTML_AND_PDF`
- `PDF`
- `DOCX_AND_PDF`
- `HWP`
- `HWPX`

The foundation intentionally does not ship a real converter yet. If a request
requires `HTML`, `PDF`, `HWP`, or `HWPX` and no matching exporter is configured,
generation fails with `DOCUMENT_EXPORTER_NOT_CONFIGURED`. This makes missing
infrastructure obvious while keeping the render/export boundary stable.

## Reviewed HWP Source Templates

The following downloaded HWP files were inspected through HWP preview text
(`PrvText`) to identify real form structure:

- `[별지 1] 감리보고서(건축공사 감리세부기준).hwp`
- `[별지 2] 공사감리일지(건축공사 감리세부기준).hwp`
- `[별지 1] 해체공사 안전점검표(건축물 해체계획서의 작성 및 감리업무 등에 관한 기준).hwp`
- `[별지 2] 해체 공사감리일지(건축물 해체계획서의 작성 및 감리업무 등에 관한 기준).hwp`
- `[별지 3] 건축물 해체감리완료 보고서(건축물 해체계획서의 작성 및 감리업무 등에 관한 기준).hwp`

Initial implementation maps `[별지 2] 공사감리일지(건축공사 감리세부기준)` into a
DOCX render smoke test because it is a compact daily report shape:

- `일련번호`
- `총괄감리책임자`
- `건축사보`
- `공사명`
- `공사 년 월 일`
- `날씨`
- `공종 및 세부공정 (층)`
- `감리 항목`
- `감리내용`
- `특기사항`
- `지적사항 및 처리결과`
- `작성방법`

The smoke test is intentionally not a final customer template. It proves that a
real Korean supervision form can be represented as:

```text
templateFields
+ CHECKLIST_TABLE output section
+ stable DOCX renderer
```

Generated smoke artifact:

```text
document-engine/build/archdox-smoke/hwp-derived-construction-supervision-daily-log.docx
```

## Development Rule

Do not make `.hwp` the source of truth for business data. HWP/HWPX is an
artifact format. The source of truth is the ArchDox report snapshot plus
versioned template/layout/workflow configuration.

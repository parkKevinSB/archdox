# Korean Reference Forms

This folder stores PDF reference copies of Korean construction supervision and
demolition supervision public forms.

These files are visual/reference material only. They are not the ArchDox source
of truth for generated documents. ArchDox generation must still start from:

```text
report snapshot + template configuration + output layout
```

## Files

| File | Source form | Pages | Extracted text |
| --- | --- | ---: | ---: |
| `construction-supervision-report-appendix-1.pdf` | Construction supervision report, appendix 1 | 3 | about 3,135 chars |
| `construction-daily-supervision-log-appendix-2.pdf` | Construction daily supervision log, appendix 2 | 1 | about 516 chars |
| `[별표 1] 단계별 감리 체크리스트 대장(건축공사 감리세부기준).pdf` | Construction supervision phase/trade checklist catalog reference, revised 2020-12-24 | 178 | catalog source |
| `demolition-safety-checklist-appendix-1.pdf` | Demolition safety checklist, appendix 1 | 1 | about 607 chars |
| `demolition-daily-supervision-log-appendix-2.pdf` | Demolition daily supervision log, appendix 2 | 1 | about 349 chars |
| `demolition-completion-report-appendix-3.pdf` | Demolition supervision completion report, appendix 3 | 1 | about 697 chars |

## Usage

- Use these PDFs as official-looking layout references when designing DOCX
  templates.
- Use `document-engine/src/test/resources/reference-form-specs/` for structured
  field/section specs that tests and smoke templates can consume.
- Do not bind business logic directly to PDF geometry, page coordinates, or
  extracted text.
- If a PDF form changes, add a new reference copy or update the corresponding
  structured spec and note the reason in the commit.

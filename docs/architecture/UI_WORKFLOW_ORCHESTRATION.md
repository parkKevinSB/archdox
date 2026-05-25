# UI Workflow Orchestration

This document defines how ArchDox should structure guided user workflows in the
React client without turning the frontend into either a hardcoded screen pile or
an overbuilt second backend orchestration engine.

## Core Idea

ArchDox user screens should feel simple:

```text
project -> site -> report -> step -> document
```

Behind that simple path, the UI should be composed from explicit workflow
building blocks.

The backend uses Flower for durable orchestration. The frontend should borrow
the philosophy, not the runtime:

- explicit flow identity
- explicit current step
- clear next/previous transition rules
- save before movement
- visible progress state
- recoverable draft state
- small step components that can be recombined

The UI must not own durable backend orchestration, retries, Agent commands, or
document generation. Those remain Cloud API, Flower, Bloom, and ArchDox Agent
responsibilities. The UI observes backend state through REST polling or future
push channels and gives the user the next useful action.

## What This Is Not

Do not create a full frontend Flower clone.

Avoid:

- a generic visual workflow engine before repeated real workflows exist
- JSON-driven UI that tries to express every React detail
- one giant `UniversalStepRenderer`
- hidden magic where step transitions are hard to debug
- office-specific branching inside components such as `if officeId === ...`
- duplicated mobile and desktop workflows with different business meaning

The right MVP shape is a lightweight UI workflow layer that is easy to read and
easy to replace later.

## Recommended Building Blocks

### `UiFlowDefinition`

Describes the flow the user is moving through.

Example shape:

```ts
type UiFlowDefinition = {
  flowId: string;
  title: string;
  contextType: "REPORT" | "PHOTO_UPLOAD" | "DOCUMENT_DELIVERY";
  steps: UiStepDefinition[];
};
```

For MVP, this can live in TypeScript. Later, some parts may be resolved from
template/configuration registry definitions.

### `UiStepDefinition`

Describes one user-facing step.

Example shape:

```ts
type UiStepDefinition = {
  id: string;
  title: string;
  description?: string;
  stepType: "FORM" | "CHECKLIST" | "PHOTO" | "DOCUMENT_ACTION" | "REVIEW";
  required?: boolean;
  savePolicy: "ON_NAVIGATE" | "DEBOUNCED" | "MANUAL_SECONDARY";
};
```

The definition should describe workflow meaning, not every visual detail.

Good:

```json
{
  "id": "CHECKLIST",
  "title": "점검 항목",
  "stepType": "CHECKLIST",
  "savePolicy": "ON_NAVIGATE"
}
```

Too much too early:

```json
{
  "component": "NestedGridWithCustomCssClassAndFiveCallbacks"
}
```

### `StepRegistry`

Maps a `stepType` or stable step code to a real React component.

Example:

```ts
const reportStepRegistry = {
  FORM: ReportFormStep,
  CHECKLIST: ReportChecklistStep,
  PHOTO: ReportPhotoStep,
  REVIEW: ReportReviewStep
};
```

This keeps configuration and implementation separated:

- workflow/config decides which step exists
- registry decides which supported component renders that step
- feature hook decides loading, saving, validation, and permissions

### `StepRunner`

Renders the common step shell:

- current step title
- progress indicator
- current step component
- previous/next/submit actions
- save/saving/error state
- mobile sticky action bar
- desktop/tablet step rail or side summary

`StepRunner` should not know report-specific DTO details. It should receive a
flow model and callbacks from a feature hook.

### `useReportWorkflow`

Owns report-writing behavior:

- active step
- loaded saved steps
- dirty state
- save current step
- previous/next movement
- submit report
- validation notices
- permission-derived disabled state

This hook is the UI workflow controller for report writing. It should stay
thin enough to read, and delegate API calls to `features/reports/api.ts` and
other feature APIs.

## Report Writing Target Shape

The report writing screen should evolve toward this structure:

```text
features/reports/
  flow/
    reportFlowDefinition.ts
    reportStepRegistry.tsx
    useReportWorkflow.ts
  components/
    ReportWorkspace.tsx
    ReportStepRunner.tsx
    steps/
      ReportFormStep.tsx
      ReportChecklistStep.tsx
      ReportPhotoStep.tsx
      ReportReviewStep.tsx
  api.ts
  types.ts
```

The current `ReportWizard` can be refactored gradually:

1. Rename conceptually from "wizard" to "report step runner" in new code.
2. Extract the step shell into `ReportStepRunner`.
3. Move built-in step definitions into `flow/reportFlowDefinition.ts`.
4. Move form rendering into `steps/ReportFormStep.tsx`.
5. Wrap checklist rendering as `steps/ReportChecklistStep.tsx`.
6. Add photo and review steps when the real feature needs them.

Do not rename every existing file immediately if it creates churn. Start by
introducing the shape around the current implementation.

## User Experience Rules

The user should not need to understand ArchDox internals.

For report writing, show:

- current project/site/report context
- current step name
- required inputs
- photos/checklist items needed for this step
- inline rule or legal guidance where it helps correction
- saving/saved/error state
- previous/next/complete actions

Hide by default:

- Agent command ids
- storage adapter names
- Flower step numbers
- template revision ids
- raw JSON payloads

Those belong in admin/Ops screens, not normal field workflows.

## Autosave Policy

Baseline:

- previous/next saves current step before moving
- submit saves current step before submission
- navigation save is mandatory for report steps
- manual save is optional and secondary
- the bottom save button is a reassurance action, not the primary workflow
- movement must stop when save fails; show the decoded API error message near
  the step runner
- show step save state explicitly: idle, dirty, saving, saved, failed

Later:

- high-risk fields may validate immediately
- long text fields may use debounced autosave
- photo upload has its own upload queue state
- offline/mobile mode may add local pending drafts

The UI must always make save state visible enough that the user trusts the
workflow.

## Mobile, Tablet, Desktop

All device sizes use the same flow definition and step order.

Mobile:

- one step at a time
- top context summary
- sticky bottom previous/next/submit actions
- filters/options in bottom sheets
- report list as cards or compact rows

Tablet:

- list plus detail when space allows
- current step plus summary panel
- touch-friendly controls

Desktop:

- denser project/site/report lists
- step rail or side summary
- more status columns and filters
- optional right detail panel

The larger layouts may show more information, but must not expose a different
business workflow.

## Configuration Boundary

ArchDox should eventually support office-specific workflow differences through
configuration. The UI layer should prepare for that without overbuilding today.

Good first boundary:

- server resolves report type, target type, template/rule revisions
- client receives or locally builds a supported flow definition
- unsupported step types fail clearly with an admin-facing configuration error
- step components are stable and reusable

Do not let arbitrary customer JSON directly render arbitrary UI. Configuration
should choose from supported step types and supported field schemas.

## Implementation Sequence

### UI Phase 1: Document And Guard The Pattern

Status: this document.

Purpose:

- record the UI workflow composition philosophy
- prevent ad hoc page growth
- keep frontend and backend orchestration responsibilities separate

### UI Phase 2: Report StepRunner Refactor

Refactor the current report wizard without changing API behavior.

Expected changes:

- add `features/reports/flow/reportFlowDefinition.ts`
- add `features/reports/flow/reportStepRegistry.tsx`
- extract `ReportStepRunner`
- extract `ReportFormStep`
- keep `useReportWizard` or introduce `useReportWorkflow` as the controller
- preserve previous/next autosave behavior

This phase is mostly code organization and UX clarity.

Initial implementation status:

- `reportFlowDefinition.ts` exists and holds the first report-writing flow.
- `reportStepRegistry.tsx` maps supported step types to React step components.
- `ReportStepRunner.tsx` owns the step rail, active step surface, and navigation
  actions.
- `ReportFormStep.tsx` and `ReportChecklistStep.tsx` are the first supported
  step components.
- `ReportPhotoStep.tsx` is now wired as the `PHOTOS` step and reuses the
  photo feature's pipeline panel instead of duplicating upload logic.
- `useReportWizard` remains the controller name for now to avoid churn, but its
  responsibility matches the future `useReportWorkflow` direction.
- Phase 8-8 polish keeps previous/next/step selection as autosave transitions.
  The manual save button remains as a reassurance action, and the step runner
  now shows clearer saved/dirty/failed states in Korean.
- Report creation starts with project and site context, then document type
  selection. The selected document type previews its configured writing steps so
  the user understands what will happen before creating the report.

### UI Phase 3: Mobile-First Project/Site/Report Path

Create a simpler normal-user path:

```text
assigned project list -> site list -> report list -> report step runner
```

Desktop and tablet may show split views, but the flow remains the same.

### UI Phase 4: Document Surface

Add a user-facing document area:

- ready-to-generate reports
- generation progress
- generated artifacts
- download/delivery status
- regeneration required after report edits

This UI observes document job/delivery APIs. It does not run document
generation locally.

Initial implementation status:

- `/documents` renders `DocumentWorkspace`.
- It lists document-relevant reports and their latest document job.
- It can request a Cloud document job for a ready report.
- It polls active document jobs through TanStack Query.
- It creates delivery requests before downloading generated artifacts.
- It polls active delivery requests and shows whether an artifact is preparing
  or ready to download.
- Phase 8-8 polish makes the document surface read as a user workflow:
  submit edit revision first, generate HTML/PDF/DOCX, watch progress, then
  download the generated artifact. Revision history remains visible, with the
  newest generated job expanded and older generated jobs collapsed.

### UI Phase 4-1: Photo Surface

Add a user-facing photo area:

- report selector
- photo upload action
- upload intent/content upload/confirm flow
- photo asset readiness
- original pickup status
- derivative generation progress by polling photo rows

Initial implementation status:

- `/photos` renders `PhotoWorkspace`.
- The screen lists report contexts and loads photos for the selected report.
- Upload uses the Cloud API intent contract and uploads the browser file to the
  returned `ORIGINAL` or `WORKING` instruction.
- After upload, the UI confirms the photo and polls while upload, derivative,
  or pickup state is still active.
- User-facing thumbnail rendering is deferred until a safe authenticated
  thumbnail/content endpoint exists.

### UI Phase 5: Config-Resolved Steps

Once report patterns repeat, let configuration registry influence:

- step order
- required fields
- checklist schema
- photo requirements
- document template binding hints

Keep React components stable and supported. Do not turn configuration into a
free-form programming language.

Initial V1 implementation status:

- Cloud API exposes `GET /api/v1/inspection-reports/{reportId}/workflow-definition`.
- The endpoint resolves office/system workflow configuration first and falls
  back to a built-in report-writing flow when no usable config is published.
- The response includes report context, source metadata, selected checklist
  schema metadata, and supported UI step definitions.
- `client/web` loads this definition before rendering the report step runner.
- If the definition cannot be loaded, the client keeps the local built-in flow
  as a defensive fallback so report writing is not blocked by configuration
  rollout mistakes.

Configuration may decide step order, step labels, and supported field schemas.
React still owns the actual components through the step registry.

## Decision

ArchDox should move toward a lightweight UI workflow orchestration layer:

```text
Flow definition + Step registry + StepRunner + feature hooks
```

This gives the product the same workflow philosophy as Flower while keeping the
frontend simple, testable, and understandable.

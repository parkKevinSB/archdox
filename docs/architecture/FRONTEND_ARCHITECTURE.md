# Frontend Architecture

This document defines how ArchDox frontend applications should grow without
turning into one large screen file or an over-abstracted framework.

For the workflow-composed UI pattern, also follow
`docs/architecture/UI_WORKFLOW_ORCHESTRATION.md`.

For frontend library/framework choices, also follow
`docs/architecture/FRONTEND_STACK_DECISION.md`.

ArchDox is a document workflow and inspection orchestration product. The client
UI will become complex because users move through:

```text
project -> site -> inspection target -> report steps -> checklist -> photos
-> document generation -> artifact delivery
```

The frontend structure should make that workflow understandable while keeping
future feature additions local to the feature being changed.

## Workflow-Composed UI Model

ArchDox UI should grow like a workflow composition layer. It should not be a
pile of screens where every new requirement is manually squeezed into the
nearest component.

The UI-side model is:

```text
workspace shell
-> collection view
-> selected business context
-> step runner
-> feature surfaces
-> async progress surfaces
```

This is similar in spirit to Flower, but not the same runtime responsibility.

- Flower owns durable backend orchestration, retries, waits, and recovery.
- The frontend owns user-facing workflow composition, input state, navigation,
  and progress observation.
- UI steps must be explicit enough that project/site/report/photo/document
  flows can be combined without rewriting the whole screen.
- Backend workflow state is read through REST polling or future push events; it
  should not be guessed from local component state alone.

Large screens and small screens use the same workflow model:

- mobile serializes the flow into one focused screen at a time
- tablet may show list plus detail or current step plus summary
- desktop may show denser lists, filters, and side panels
- none of these should create a different product journey

Prefer these composition units before inventing custom one-off screens:

- `WorkspaceShell`
- `CollectionList`
- `EntitySummary`
- `WorkflowPath`
- `StepRunner`
- `FormStep`
- `ChecklistStep`
- `PhotoStep`
- `DocumentProgressPanel`
- `OptionSheet`

These names are conceptual. Do not create generic abstractions until the pattern
is used by real features. The rule is to keep workflow composition visible and
modular, not to overbuild a UI framework.

The implementation direction is:

```text
Flow definition + Step registry + StepRunner + feature hooks
```

This keeps the frontend close to Flower's step-by-step thinking while leaving
durable orchestration, retries, waiting, and recovery to Cloud API and Flower.

## Current Client Structure

`client/web` currently uses this first-stage modular structure:

```text
src/
  App.tsx
  appRoutes.ts
  appTypes.ts
  api.ts
  types.ts
  styles.css
  components/
    common.tsx
  features/
    assignments/
      AssignmentPanels.tsx
    auth/
      AuthScreen.tsx
    checklists/
      api.ts
      types.ts
      components/
        ReportChecklistPanel.tsx
      hooks/
        useReportChecklist.ts
    photos/
      api.ts
      types.ts
      components/
        PhotoWorkspace.tsx
      hooks/
        usePhotoWorkspace.ts
    reports/
      api.ts
      flow/
        reportFlowDefinition.ts
        reportStepRegistry.tsx
      reportTypes.ts
      reportSteps.ts
      types.ts
      components/
        ReportList.tsx
        ReportStartForm.tsx
        ReportStepRunner.tsx
        ReportWizard.tsx
        steps/
          ReportChecklistStep.tsx
          ReportFormStep.tsx
      hooks/
        useReportWizard.ts
    documents/
      api.ts
      types.ts
      components/
        DocumentWorkspace.tsx
      hooks/
        useDocumentWorkspace.ts
```

### `App.tsx`

Responsibilities:

- app bootstrapping
- auth state selection
- deriving the account's fixed workspace context
- top-level workspace data loading
- top-level navigation between major screens
- composing feature views

Rules:

- Do not keep adding new full feature screens into `App.tsx` forever.
- `App.tsx` may coordinate data and routing, but feature-specific UI should move
  into `features/*` once it starts to grow.
- Avoid putting photo/document workflow business rules directly in `App.tsx`.
- The normal client app must not expose a personal/office selector. A personal
  account works in its personal workspace; an office-invited account works in
  that office workspace. Legacy multi-membership data may be resolved to a
  deterministic internal default, but switching is not a normal user feature.
- Raw API status codes such as `403` must be translated into user-facing next
  actions. For report editing, prefer messages that explain whether project or
  report assignment is required.

Top-level navigation:

- `App.tsx` derives the current major view from `React Router` location.
- `src/appRoutes.ts` owns route-to-view mapping.
- Top-level paths express workflow areas: `/projects`, `/reports`, `/photos`,
  `/documents`, and `/more`.
- Do not reintroduce broad local-only view state for major pages. Small local
  tabs or filters may still use component state.

Current project workspace layout:

- `ProjectsView` presents the normal user path as a workflow board:
  `1. 프로젝트 -> 2. 현장 -> 3. 리포트`.
- Report selection from that board opens the report writing workspace at
  `/reports`.
- Inspection targets and project assignments are support panels below the
  primary workflow board.
- This is an intentionally lightweight composition. It is not a generic CRUD
  framework.

Current report workspace layout:

- `/reports` prioritizes writing: report list plus selected report step runner.
- `ReportWizard` no longer embeds report assignment management inside the
  writing flow.
- `ReportAssignmentPanel` is rendered as a support panel outside the step
  runner.
- This keeps normal report writing focused while preserving admin/member
  management for users who need it.

### `features/auth`

Owns:

- login UI
- personal signup UI
- office invitation signup UI
- invitation preview loading

This feature is separated early because signup and invitation rules are security
sensitive and will grow with email verification.

### `features/assignments`

Owns:

- project assignment panel
- report assignment panel
- assignment list loading, saving, and removal UI
- assignment-specific empty, notice, and error states

Rules:

- Assignment UI may be embedded inside project/report screens, but the
  assignment behavior itself lives in this feature module.
- Office `OWNER`/`ADMIN` users can manage assignments in the MVP UI.
- Other office users may read visible assignment lists when the API allows it,
  but the Cloud API remains the source of truth for final permission checks.
- Do not duplicate the full permission matrix inside visual components. Use
  `domain/permissions.ts` for office-level checks and loaded assignment data for
  project/report-specific checks.

### `features/reports`

Owns:

- report list UI
- report start form
- report type options
- report step definitions
- report step payload conversion helpers
- report step loading API
- `useReportWizard` state transition hook

Current split:

- `features/reports/components/ReportWizard.tsx` renders the first MVP report
  wizard screen and composes report context, assignment panel, notices, and the
  step runner.
- `features/reports/components/ReportStepRunner.tsx` renders the step rail,
  active step component, previous/next/submit actions, and save/progress shell.
- `features/reports/components/steps/ReportFormStep.tsx` renders form-based
  report steps.
- `features/reports/components/steps/ReportChecklistStep.tsx` composes the
  form summary and checklist feature panel for checklist steps.
- `features/reports/components/steps/ReportPhotoStep.tsx` composes the shared
  photo pipeline panel inside the report-writing step flow.
- `features/reports/components/ReportList.tsx` renders report selection lists.
- `features/reports/components/ReportStartForm.tsx` renders the report creation
  form and uses React Hook Form.
- `features/reports/hooks/useReportWizard.ts` owns active step, saved steps,
  step loading, save, next/previous movement, submit, notices, and errors.
- `features/reports/flow/reportFlowDefinition.ts` owns the current lightweight
  report flow definition.
- `features/reports/flow/reportStepRegistry.tsx` maps supported report step
  types to React step components.
- `features/reports/reportTypes.ts` owns report type options used by report
  creation UI.
- `features/reports/reportSteps.ts` keeps step-code checks and payload mapping
  helpers while re-exporting the active flow step definitions.
- `features/reports/api.ts` owns report-step REST reads.
- `App.tsx` injects `features/checklists/components/ReportChecklistPanel.tsx`
  into `ReportWizard` through a render prop. This keeps `ReportWizard` focused
  on report-step composition while checklist answer editing remains a separate
  feature.

Next split:

- move report save/submit mutations into `features/reports/api.ts`
- move the remaining `ReportsView` layout shell out of `App.tsx` when report
  page orchestration grows again

Report wizard navigation rules:

- `이전` and `다음` automatically save the current step before moving.
- A separate primary save button is not part of the normal writing flow.
- Submit saves the current step first, then submits the report.
- Submit failures with `blockingIssues` are readiness guidance, not generic
  network failures. Display the server messages clearly so the user knows which
  step, checklist, or photo input is missing.
- Submitted or generated reports are read-only in the step runner until the
  user explicitly creates an edit revision. The UI should call
  `POST /inspection-reports/{reportId}/reopen`, then continue normal autosave
  against the incremented `contentRevision`.
- Report edit/reopen controls must use `InspectionReportResponse.writeAllowed`
  and `reopenAllowed`, not only the active office role. A general `MEMBER` role
  can still be narrowed by project/report assignments.
- Server `currentStep` is a resume hint when a report is opened. It must not
  pull the user back to the previously saved step after the user has already
  moved to another step.

### `features/checklists`

Owns:

- report checklist loading
- checklist answer editing
- checklist answer save mutation
- report target attachment from the checklist workflow

Current split:

- `features/checklists/api.ts`: checklist REST calls and report-target attach
  call used by the checklist workflow
- `features/checklists/hooks/useReportChecklist.ts`: TanStack Query server
  state for checklist schema/answers and mutations
- `features/checklists/components/ReportChecklistPanel.tsx`: dynamic checklist
  form powered by React Hook Form
- `features/checklists/types.ts`: checklist feature request/form type aliases

Rules:

- Checklist UI must not call the root `api.ts` directly.
- Dynamic checklist answer forms should use React Hook Form so schema-driven
  fields can grow without turning into ad hoc state maps.
- Checklist schema and answers are server state and should be read/mutated
  through TanStack Query hooks.

### `features/photos`

Owns:

- report-level photo list loading
- upload intent creation
- browser file upload to API-local or S3-compatible upload URLs
- upload confirmation
- photo asset and pickup status display
- photo-specific request/response types

Current split:

- `features/photos/api.ts`: photo list, upload intent, content upload, and
  confirm calls
- `features/photos/types.ts`: photo, asset, intent, and upload instruction DTOs
- `features/photos/hooks/usePhotoWorkspace.ts`: TanStack Query polling and
  upload mutation flow
- `features/photos/hooks/usePhotoAssetPreview.ts`: authenticated preview fetch
  and browser object URL lifecycle
- `features/photos/components/PhotoWorkspace.tsx`: user-facing photo workspace
  for `/photos`
- `features/photos/components/PhotoPipelinePanel.tsx`: reusable report-level
  upload/status surface used by both `/photos` and report-writing photo steps

Rules:

- Photo upload is a report workflow surface, not a generic media library.
- The report-writing flow may embed the same photo pipeline as a `PHOTO` step.
  Keep upload/status logic in `features/photos`; report steps should compose it
  rather than duplicating upload behavior.
- The UI creates an upload intent, uploads the selected image to the returned
  instruction, confirms the upload, then observes derivative/pickup state.
- The normal user screen may show simple asset readiness, but should not expose
  raw storage references as a required concept.
- Thumbnail/working previews must be fetched with auth headers and rendered via
  browser object URLs. Do not use raw API URLs directly as `<img src>` values.
- User screens may request `THUMBNAIL` or `WORKING` content. They must not
  request `ORIGINAL` content through the browser preview API.
- Active photo rows should be polled while upload, derivative generation, or
  original pickup is still pending.

### `components/common`

Owns small reusable UI primitives:

- `Panel`
- `ViewHeader`
- `EmptyState`
- `StatusBadge`
- `InlineAlert`
- `InlineNotice`
- `BrandLogo`

Rules:

- Put only product-generic UI components here.
- Do not put project/report/checklist business behavior in common components.
- Do not create large generic abstractions like `GenericCrudPage` or
  `UniversalFormEngine` without a repeated proven need.

### `appTypes.ts`

Owns app-level types that are not domain API DTOs, such as authenticated app
state.

### `types.ts`

Owns DTO-like client types that mirror Cloud API responses.

Future option: once the REST API stabilizes, generate typed clients from an
OpenAPI contract instead of manually duplicating DTO shapes.

## Target Growth Shape

As the product grows, split by business feature, not by technical artifact only.

Recommended next shape:

```text
src/
  app/
    App.tsx
    layout/
    routing/
    workspaceState.ts

  features/
    auth/
    assignments/
    invitations/
    projects/
    sites/
    targets/
    reports/
    checklists/
    photos/
    documents/
      api.ts
      types.ts
      components/
        DocumentWorkspace.tsx
      hooks/
        useDocumentWorkspace.ts
    office/

  components/
    common/
    forms/
    layout/

  api/
    authApi.ts
    officeApi.ts
    projectApi.ts
    reportApi.ts
    checklistApi.ts
    photoApi.ts
    documentApi.ts

  domain/
    codes.ts
    permissions.ts
    reportSteps.ts
```

Do not create this whole tree before the features need it. Split when one of the
triggers below is met.

## Split Triggers

Move code from `App.tsx` or a large feature file when:

1. A component exceeds roughly 250-350 lines and contains more than one
   business responsibility.
2. The same state/handler pair is needed by two screens.
3. A feature needs its own loading/error/save lifecycle.
4. A feature gets its own permission rules.
5. A feature starts touching more than one API endpoint.
6. A feature will be worked on independently, such as photos, checklist editing,
   document progress, or project member permissions.

Examples:

- Checklist answer editing should live in `features/checklists`.
- Photo upload, asset status, and upload queue should live in `features/photos`.
- Document job polling, progress, download, and delivery request UI should live
  in `features/documents`.
- Project creation, site lists, and target lists may stay together early, but
  should split when project permissions and assignment UI arrive.

### `features/documents`

Owns:

- document generation request UI
- document job list/progress polling
- artifact delivery/download request UI
- document-specific request/response types

Current split:

- `features/documents/api.ts`: document job, delivery request, and authorized
  download/preview fetch calls
- `features/documents/types.ts`: document job/artifact/delivery response types
- `features/documents/hooks/useDocumentWorkspace.ts`: TanStack Query polling
  and document mutations, including HTML preview state
- `features/documents/components/DocumentWorkspace.tsx`: user-facing document
  workspace for `/documents`

Rules:

- Normal user document screens must show report/document state, not Flower or
  Agent internals.
- Document screens are revision-aware. Show generated jobs grouped by
  `reportRevision`, distinguish latest generated revision from previous
  generated revisions, and guide `STEP_SAVED` edit revisions to submit before
  generation.
- REST generation requests return immediately; UI observes progress by polling
  document job endpoints.
- Artifact downloads must go through authenticated API calls because document
  endpoints require `Authorization` and `X-Office-Id`.
- HTML previews must also go through authenticated API calls. Render downloaded
  HTML inside a sandboxed iframe and treat it as a review surface, not as a
  final PDF/page-break guarantee.
- Delivery requests may be asynchronous. The document feature should show
  request/preparing/downloadable states and poll delivery requests while they
  are active.

## API Layer Direction

Current MVP has one `api.ts`.

This is acceptable while endpoints are few, but the next growth step should be:

```text
api/
  http.ts
  authApi.ts
  officeApi.ts
  projectApi.ts
  reportApi.ts
  checklistApi.ts
  photoApi.ts
  documentApi.ts
```

Rules:

- `http.ts` should own the fetch wrapper, auth header, office header, and error
  normalization.
- Feature API files should expose named functions, not classes.
- UI components should call feature API functions or feature hooks, not build
  URLs directly.
- Keep API DTO names aligned with `docs/architecture/API_CONTRACT.md`.

## Feature Module Implementation Rules

As ArchDox grows, feature code should follow this direction:

```text
features/
  reports/
    api.ts
    types.ts
    hooks/
      useReportWizard.ts
    components/
      ReportWizard.tsx

  assignments/
    api.ts
    types.ts
    hooks/
      useProjectAssignments.ts
      useReportAssignments.ts
    AssignmentPanels.tsx
```

Rules:

- Do not put direct REST calls inside visual components once a feature has its
  own loading, save, retry, or error lifecycle.
- Put feature-specific API functions in `features/*/api.ts`.
- Put feature-specific request/response types in `features/*/types.ts`.
- Shared DTOs may stay in `src/types.ts` until the API contract is generated or
  split. Do not duplicate shared DTO shapes just to satisfy folder purity.
- Use React Hook Form for forms with validation, reset behavior, or more than a
  couple of fields.
- Simple local UI state such as selected tab, open/closed panel, or a one-field
  search box may remain plain React state.
- Use TanStack Query for server state such as project lists, report lists,
  assignments, checklist answers, document jobs, artifact delivery requests, and
  polling workflows.
- Visual components should prefer feature hooks over calling API functions
  directly.

This is a growth rule, not a command to over-abstract every small MVP component.
Apply it when the feature starts to own real business state.

## Report Wizard Direction

`ReportWizard` must not become the business workflow engine.

Target split:

```text
features/reports/
  components/
    ReportWizard.tsx
    ReportStepForm.tsx
    ReportChecklistPanel.tsx
  hooks/
    useReportWizard.ts
  api.ts
  types.ts
```

Responsibilities:

- `ReportWizard`: screen composition and layout only.
- `useReportWizard`: active step, next/previous navigation, autosave trigger,
  save state, submit state, dirty state, and permission-derived disabled state.
- `features/reports/api.ts`: report, step, checklist, and submit HTTP calls.
- `features/reports/types.ts`: report wizard view models and feature-local DTOs.

Report step transitions are workflow behavior. Keep them in hooks or workflow
services, not scattered across button handlers in JSX.

## Permission Direction

Permissions should not be scattered across buttons and pages.

The client has a small domain utility for first-stage office permissions:

```text
domain/permissions.ts
```

Examples:

- personal owner can create personal projects
- office `OWNER` and `ADMIN` can create office projects
- office `MEMBER` may write assigned reports
- office `VIEWER` is read-only

UI should use permission helpers for visibility/disabled states, while Cloud API
remains the source of truth and must enforce the same rules server-side.

Current MVP helpers:

- `canManageProjects(office)`: personal `OWNER`, or office `OWNER`/`ADMIN`.
  Used for project creation and assignment-management controls. Project
  `MANAGER` assignments are project-specific and must be checked with loaded
  assignment data before enabling site/target management for non-admin members.
- `canWriteReports(office)`: personal `OWNER`, or office
  `OWNER`/`ADMIN`/`MEMBER`. Used for report start, report step save,
  checklist answer save, report-target attachment, and submit controls. This is
  only the office-level baseline; project/report assignments can narrow access
  once assignment lists exist.

Rule: do not inline these role checks in every component. Add/adjust a domain
helper first, then consume it from screens.

Project/report assignment API and server state are now feature-owned:

- `features/assignments/api.ts`: assignment REST calls
- `features/assignments/types.ts`: assignment form/request/response aliases
- `features/assignments/hooks/useProjectAssignments.ts`: project assignment
  query and mutations
- `features/assignments/hooks/useReportAssignments.ts`: report assignment query
  and mutations
- `features/assignments/AssignmentPanels.tsx`: visual panels and React Hook Form
  forms

`getOfficeMembers` still lives in the root `api.ts` because office membership is
shared outside assignments. Move it into an office feature API when office
screens are split from `App.tsx`.

## Workflow UI Direction

ArchDox UI should reflect workflow state clearly:

- report draft/saved/submitted states
- checklist completion state
- photo upload and derivative state
- document job progress
- artifact delivery state
- Agent command state when relevant

Long-running operations should not block the screen. REST commands create a job
or request and return immediately. The UI reads progress through polling or a
future event stream.

Normal user screens should hide internal architecture unless it directly helps
the task. A field inspector should mostly see:

```text
project -> site -> report -> step -> document
```

Examples:

- A report writing screen should show the current step, required inputs,
  validation guidance, autosave status, and the next action.
- A photo step should show capture/upload state and retry affordances, not
  storage adapter details.
- A document screen should show whether a report can generate, is generating,
  is ready to download, or needs regeneration after edits.
- Legal or rule-based guidance should appear inline or as a focused warning at
  the step where the user can fix it.

Autosave and navigation:

- Previous/next actions save the current step before moving.
- Important field changes may later use debounced autosave, but navigation save
  remains the baseline.
- Manual save may exist as a secondary debug/admin convenience, not the primary
  normal-user path.
- The UI should show a small saved/saving/error state so the user trusts the
  flow without needing to understand the API.

## What To Avoid

Avoid:

- one giant `App.tsx` that owns every screen and form
- office-specific UI branches such as `if (officeId === 123)`
- generic CRUD abstractions before repeated patterns are proven
- deeply nested component folders with only one file each
- duplicating permission logic in many screens
- putting API URL strings inside visual components
- mixing admin-only operational density into normal user workflows
- creating separate mobile/tablet/desktop workflows that diverge in business
  meaning
- hiding step transitions inside unrelated JSX button handlers
- making report-writing users understand Agent, storage, Flower, or template
  internals during normal data entry

## Practical Rule

Prefer modest, feature-shaped modules:

```text
one feature = one business area = its screen components + small helpers
```

Split late enough that the boundary is clear, but early enough that adding the
next major feature does not require rewriting the page.

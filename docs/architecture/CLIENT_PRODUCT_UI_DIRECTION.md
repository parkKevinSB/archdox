# Client Product UI Direction

This document defines the visual and layout direction for the real ArchDox user
application. It shares the same product family and visual system as the
`admin` operations console.

For code organization, feature boundaries, and growth rules, also follow
`docs/architecture/FRONTEND_ARCHITECTURE.md`.

Feedback from early review preferred the existing `admin` console look over the
separate blue product-shell direction. Therefore `client/web` should now use an
admin-aligned theme: dark ArchDox sidebar, light gray workspace, white panels,
compact controls, and a calm teal/green accent. The user app must still simplify
normal field/report workflows instead of exposing dense administrator-only
surfaces.

## Reference Feeling

The current reference is the ArchDox `admin` React UI, not a separate marketing
or blue SaaS shell.

The desired takeaway is:

- same ArchDox product identity across admin and client
- dark left navigation on desktop and large tablet
- light gray workspace with white operational panels
- compact buttons, selects, filters, lists, and status badges
- table/list-centered productivity layout on large screens
- calmer, simpler task flow for normal users
- strong sense of "work tool", not a landing page

## Product Personality

ArchDox user UI should feel like:

- document workflow workspace
- inspection/report writing cockpit
- calm, precise, trustworthy
- easy for non-technical office users
- fast to scan on PC and iPad
- comfortable for photo/checklist work on mobile

Avoid:

- marketing hero layouts inside the app
- oversized decorative cards
- dark dashboard-heavy appearance
- purple/gradient-heavy SaaS styling
- office-specific hardcoded visual branches
- exposing admin-only density to normal users
- a separate client theme that feels like a different product

## Workflow Composition Principle

The normal user UI must be designed as a guided workflow surface, not as a set
of unrelated CRUD pages that are forced together later.

ArchDox has the same core journey on phone, tablet, and PC:

```text
login -> assigned projects -> sites -> reports -> report writing steps
-> photos/checklists -> document generation -> document delivery
```

Device size changes how much context is visible, not the mental model.

- Mobile shows one focused workflow step at a time.
- Tablet may show the current list and selected detail together.
- Desktop may show denser lists, filters, status columns, and a side summary.
- All three should still feel like the same workflow, with the same objects and
  step order.

Use the same philosophy as backend Flower orchestration, but on the UI side:

- explicit current step
- clear next action
- visible saved/progress state
- recoverable draft state
- small composable step surfaces
- no hidden office-specific branches in components

The frontend does not run backend Flower flows. It mirrors workflow state and
lets the user advance through UI steps while Cloud API and Flower own durable
business progression.

Recommended UI building blocks:

- `CollectionList`: projects, sites, reports, documents
- `EntitySummary`: selected project/site/report context
- `StepRunner`: report/checklist/photo writing sequence
- `PhotoCaptureSurface`: capture, upload, retry, thumbnail review
- `DocumentActionSurface`: generate, progress, download, regenerate
- `OptionSheet`: per-screen preferences and switches

Build screens by composing these workflow blocks. Avoid building one-off pages
that cannot be reused in the next project/site/report flow.

## Color Direction

Use the admin-aligned ArchDox palette, balanced with neutral surfaces and a
small number of semantic accents.

Suggested palette:

- Sidebar dark: `#172322`
- Sidebar hover: `#253634`
- Brand mark: `#21413B`
- Primary action: `#1D5D51`
- Focus/accent: `#2A7F70`
- App background: `#EEF2F4`
- Panel surface: `#FFFFFF`
- Border: `#D8E1DF`
- Text primary: `#14201F`
- Text secondary: `#60746F`
- Muted text: `#6F8581`
- Success/online: `#14765F`
- Selection highlight: soft mustard/amber, for example `#C99A28` accent on
  `#FFF4D6` background
- Warning/in-progress: `#9A5C00`
- Error/destructive: `#C83A32`

Rules:

- Dark green/teal may define navigation and primary actions, but the content
  area should remain mostly white and neutral.
- Use teal/green for product identity, success, and primary actions.
- Use the soft mustard highlight for selected rows, active wizard steps,
  current workflow position, and other "look here" states. This keeps selection
  distinct from green success/primary actions.
- Keep ordinary mouse hover states neutral light gray. Hover must not reuse the
  selection highlight color, because users need to distinguish "selected/current"
  from "the pointer is here".
- Reserve stronger orange/amber for pending/in-progress/warning. Selection
  highlight should stay lighter and calmer than warning states.
- Use red only for failure/destructive actions.
- Avoid making every card, badge, and section a shade of green.

## Desktop And Large Tablet Layout

Target: desktop browser, laptop, iPad landscape, large tablet.

Primary structure:

```text
left dark navigation
top workspace bar
main work area
optional right detail/summary panel
```

Top workspace bar:

- fixed height around `56-64px`
- white/light workspace background
- global search or quick command area
- office/project switcher
- sync/upload status
- notification icon
- user menu

Left navigation:

- visible on desktop and iPad landscape
- dark ArchDox sidebar, width around `220-260px`
- clear active state
- menu examples:
  - Dashboard
  - Projects
  - Reports
  - Photos
  - Document Jobs
  - Templates
  - Settings
- icons plus labels
- no nested deep menus early

Main work area:

- light gray page background
- white panels for actual tools/lists
- 8px radius max unless a component system requires otherwise
- consistent `16-24px` spacing
- primary workflow appears in the first viewport
- avoid page sections styled as big floating cards

Common large-screen patterns:

- project/report list with search and filters
- split view: list left, selected item detail right
- wizard view with step navigation and a stable work panel
- photo grid with detail drawer
- document job progress panel with polling state

## iPad And Tablet Behavior

Target: iPad portrait/landscape and Android tablets.

Landscape:

- same mental model as desktop
- left navigation remains visible if width allows
- split views are allowed
- toolbars should stay touch-friendly

Portrait:

- left navigation may collapse to icon rail or drawer
- main workflow stays single focused column
- secondary detail panels become drawers or stacked sections
- avoid cramming full desktop tables into narrow width

Tablet-specific rules:

- touch targets should be at least `40px`, preferably `44px`
- important form actions should not require hover
- use segmented controls, tabs, drawers, and bottom sheets for mode changes
- photo capture/upload controls should be easy to hit with a finger

## Mobile Layout

Target: normal smartphone portrait first.

Mobile must not be a squeezed desktop table.

Primary structure:

```text
compact top bar
single-column content
bottom navigation or sticky workflow actions
bottom sheet/drawer for filters and secondary actions
```

Mobile navigation:

- use bottom navigation for core user areas:
  - Home
  - Projects
  - Capture
  - Reports
  - More
- use top app bar for title, back, search, and sync status
- avoid permanent side navigation on phones

Mobile workflow:

- report writing should be step/wizard based
- one major task per screen
- sticky bottom action:
  - Next
  - Submit
  - Upload retry
- autosave indicator should be visible, but manual save should not be the
  primary flow
- filters open as bottom sheet
- tables become cards or compact list rows
- detail views are full-screen pages, not tiny side panels

Mobile photo/checklist UX:

- checklist items should be large rows
- photo capture button should be prominent
- show thumbnail strip/grid after capture
- thumbnails are for recognition, not final review; tapping/clicking a
  thumbnail should open a larger working-image preview when available
- do not show original images by default in the UI; original access follows the
  storage/agent policy
- upload/sync state should be visible but not noisy
- offline queue count should be visible near top or bottom action area

## Components

Use familiar controls:

- icon buttons for common actions such as refresh, upload, download, delete
- segmented controls for modes
- tabs for major subviews
- toggles/checkboxes for boolean settings
- inputs/selects for form values
- sliders/steppers only for numeric adjustment where useful
- drawer/bottom sheet for mobile filters/actions
- table on desktop, card/list rows on mobile

Cards:

- use cards for repeated items, detail summaries, and modal-like surfaces
- do not put cards inside cards
- do not make every page section a floating card
- keep radius around `6-8px`

Tables:

- desktop tables should be dense but readable
- status badges should be short and semantic
- actions should use icons with tooltips when possible
- mobile should convert tables to list/card rows

## Typography

- Use system UI or Inter-like sans-serif.
- Keep letter spacing `0`.
- Do not scale font size with viewport width.
- Desktop content heading: `20-26px`
- Panel heading: `15-18px`
- Body/list text: `13-15px`
- Mobile body text should not be too small; prefer `15-16px`.
- Reserve hero-scale typography for public/marketing pages, not app screens.

## Responsive Breakpoints

Suggested breakpoints:

- Mobile: `< 768px`
- Tablet: `768px - 1199px`
- Desktop: `>= 1200px`

Behavior:

- Desktop: left nav + main + optional right panel
- Tablet landscape: left nav + main, optional drawer detail
- Tablet portrait: collapsed nav + main column
- Mobile: top bar + single column + bottom nav/sticky action

## Page Examples

### Project List

Desktop:

- left nav
- top search
- filter row
- table or dense list
- selected project detail panel
- `업무 유형` is a select, not free text.
- `현장 유형` is a select, not free text.
- `점검 대상 유형` is a select, not free text.
- UI labels are Korean; stored values are stable uppercase codes.

Mobile:

- top search
- project cards/list rows
- filter bottom sheet
- project detail opens full screen

### Main User Journey

Mobile-first flow:

1. Login stays remembered on trusted personal devices through a secure session
   and refresh-token model.
2. The first screen shows only projects assigned to the user, or personal
   projects for a personal account.
3. Selecting a project opens its site list.
4. Selecting a site opens report lists grouped by draft, ready to generate,
   generated, and completed.
5. The plus action creates the next object in context: site under project,
   report under site.
6. Opening a report starts or resumes the report step runner.
7. Previous/next automatically saves the current step before moving.
8. Completion validates required fields, checklist answers, and photo
   requirements before moving the report to document-ready state.
9. The document tab shows reports that can generate documents, generation
   progress, generated artifacts, and regeneration actions after edits.

Account and workspace rule:

- The normal `client/web` app must not let a user freely switch between a
  personal workspace and an office workspace. Account identity decides the
  workspace line.
- A `PERSONAL` signup lands in that user's personal workspace. An `OFFICE`
  signup through an invitation lands in the invited office workspace.
- If legacy/dev data temporarily gives a user more than one membership, the
  client may choose a deterministic default internally, but it must not expose a
  workspace selector in the normal user path.
- Admin/Ops controls belong in `admin`, not in the normal report-writing client.
  The client may show assignment support panels only when they are part of the
  user's project/report context and allowed by role.
- Permission failures must be explained as next actions, for example "리포트
  WRITER 배정이 필요합니다", not shown as a raw `403`.

Large-screen flow:

- PC/tablet may show more rows, filters, status, and a detail panel, but should
  not expose a different workflow.
- The user should still understand the product as project -> site -> report
  -> document.
- Admin/Ops data should stay out of the normal user path unless the user's role
  and task require it.

Current client workspace direction:

- The project screen is a workflow board, not a generic CRUD dashboard.
- The primary visible sequence is `1. 프로젝트 -> 2. 현장 -> 3. 리포트`.
- Selecting an existing report from the project/site context opens the report
  writing workspace.
- Support panels such as inspection targets and project assignments sit below
  the primary workflow so they do not interrupt the normal path.
- Desktop shows the three workflow columns together. Mobile stacks them in the
  same order.

### Report Wizard

Desktop/iPad:

- step list on left or top
- main form panel
- right progress/photo summary panel if width allows

Mobile:

- one step at a time
- compact progress indicator
- sticky previous/next/submit actions
- current step auto-saves before navigation
- photo/checklist sections as large touch rows

Current MVP foundation:

- Report creation starts from a selected `Project` and `Site`.
- The report workspace shows the report list on the left and a step-based
  writer on the right for desktop/iPad.
- The wizard saves draft data by `stepCode` through
  `/inspection-reports/{reportId}/steps/{stepCode}`.
- The wizard resumes draft data through
  `/inspection-reports/{reportId}/steps`.
- Initial built-in steps are `BASIC_INFO`, `WORK_SUMMARY`, `CHECKLIST`,
  `PHOTOS`, and `REMARKS`.
- The `CHECKLIST` step loads the resolved checklist schema and saves answers by
  item code. Answers may be report-level or target-specific.
- The `PHOTOS` step reuses the same photo upload/pipeline surface as the
  `/photos` workspace, so users can add required report photos without leaving
  the writing flow.
- These built-in steps are only the first UI skeleton. The target design is to
  resolve wizard steps, required fields, checklist shape, and output binding
  from template/configuration registry definitions.
- Submitting the wizard moves the report to `READY_TO_GENERATE` only after
  Cloud validates readiness. Missing basic info, checklist input, or working
  photos must be shown as clear next actions, not as a generic error.
- Document generation is still a separate asynchronous workflow after the
  report becomes ready.
- Generated reports remain editable through an explicit "create edit revision"
  action. The UI must not overwrite a generated artifact in place. It should
  keep generated document jobs as revision history: the newest generated job is
  expanded by default and older generated jobs are shown as collapsible previous
  versions. If there is only one generated job, the UI should clearly say that
  no previous generated version exists yet.
  reopen the report as the next content revision, then require validation and
  document regeneration for that new revision.

Current report workspace direction:

- `/reports` is a writing workspace first.
- The visible priority is report list -> selected report -> step runner.
- Assignment management is a support panel outside the writing runner, not part
  of the normal step flow.
- The workspace may show small summary metrics such as draft, ready to generate,
  and generated counts.
- Normal users should see autosave and document-generation guidance as simple
  writing rules, not backend internals.

### Photo Gallery

Desktop/iPad:

- grid thumbnails
- filters/search
- right detail drawer

Mobile:

- 2-column or 3-column thumbnail grid
- full-screen photo detail
- bottom action bar for replace/delete/attach

Current MVP photo workspace:

- `/photos` is a report-level photo pipeline surface.
- The user first selects a report context, then uploads photos into that report.
- Upload follows the Cloud API intent model: create intent, upload to the
  returned API-local or S3-compatible URL, confirm upload, then observe status.
- The screen shows original pickup status, working image status, and thumbnail
  status as simple readiness chips.
- For office plans, the copy should reinforce that originals move to ArchDox
  Agent/NAS and cloud temporary originals are not the long-term source of truth.
- Preview rendering uses the authenticated photo asset content endpoint. Browser
  code should prefer `WORKING` for the visible card preview, then fall back to
  `THUMBNAIL`, so the user can actually recognize the photo subject.
- Photo card previews should be large enough to identify the content. MVP card
  previews use roughly `92px` square thumbnails, not tiny icon-sized previews.
- The normal user UI does not preview `ORIGINAL` through the browser. Original
  content belongs to the office Agent/NAS retention policy.
- Mobile should emphasize one large upload/capture action and a compact photo
  card grid. Desktop can show report selection, photo grid, and policy/context
  panels together.

### Document Workspace

The `/documents` screen is the user-facing document generation surface.

It should show:

- reports ready for document generation
- generation in progress
- generated artifacts
- download actions
- failed jobs with retry affordance

It should not expose:

- Flower step numbers
- Agent command ids
- storage adapter details
- raw artifact storage references

Current MVP behavior:

- Reports in `READY_TO_GENERATE`, `GENERATION_REQUESTED`, `GENERATING`,
  `GENERATED`, or `FAILED` appear in the document workspace.
- Reports in `STEP_SAVED` also appear when `contentRevision` is newer than
  `submittedRevision`; the screen should explain that the user must submit the
  edit revision before generating a new document.
- Document cards show `contentRevision`, `submittedRevision`,
  `generatedRevision`, and the latest job's `reportRevision` as compact
  revision chips.
- Generated jobs are shown as revision history. The latest generated revision
  is visually distinguished from previous generated revisions.
- Document generation requests use the Cloud worker by default for the first
  client UI.
- HTML preview requests are a separate user action from DOCX generation. The UI
  creates an `outputFormat=HTML` document job when the user wants a browser
  preview, then shows the generated HTML artifact in an in-app sandboxed
  preview dialog.
- HTML artifact rows expose both preview and download actions. Preview fetches
  the HTML through authenticated Cloud API calls and renders it with iframe
  `srcDoc`; it must not depend on an unauthenticated public file URL.
- The UI polls document jobs while they are active.
- Download actions create delivery requests and download through authenticated
  Cloud API calls.
- If a delivery request is not immediately downloadable, the UI shows a
  preparing state and polls delivery request status. This supports future
  ArchDox Agent/NAS-backed artifact delivery without changing the user-facing
  flow.

## Admin Versus User UI

Shared ArchDox UI system:

- dark left sidebar on large screens
- light gray workspace
- white bordered panels
- compact buttons, selects, filters, list rows, and status badges
- teal/green primary action and focus color
- current `admin` module style is the baseline

User application:

- uses the same theme and product identity
- cleaner in workflow composition
- more guided in copy and empty states
- fewer columns at once
- stronger workflow affordance
- better mobile/touch behavior
- no admin-only monitoring/management clutter unless the workflow requires it

Reuse the admin visual system, but do not expose admin-only information
architecture to normal users.

## Implementation Rules

When implementing `client/web`:

1. Build the real workflow screen first, not a marketing landing page.
2. Use React components that can later run inside Capacitor mobile shell.
3. Keep desktop and iPad productive, but make mobile a first-class flow.
4. Use responsive layout rules rather than separate duplicated pages.
5. Keep office/customer-specific UI differences configuration-driven where
   possible.
6. Do not expose admin-only operational concepts in normal user screens unless
   the workflow needs them.

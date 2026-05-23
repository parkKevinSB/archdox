# Frontend Stack Decision

This document records the frontend stack direction for ArchDox client/admin
React applications.

## Decision Summary

ArchDox should stay on a Vite + React + TypeScript SPA foundation for the MVP.
Do not move to Next.js now.

Recommended stack:

```text
Vite + React + TypeScript
React Router
TanStack Query
React Hook Form
Zod
shadcn/ui + Radix UI + Tailwind CSS
Storybook
Vitest
XState only when a UI workflow becomes truly state-machine heavy
```

## Why Not Next.js Now

Next.js is strong for full-stack React, SSR, SEO, public marketing pages, and
server components. ArchDox already has a Spring Cloud API and the main user app
is an authenticated workflow application.

For MVP, Next.js would add another server/runtime mental model without solving
the hardest ArchDox problems:

- report writing workflow
- project/site/report hierarchy
- photo upload and pickup state
- document generation progress
- agent command and artifact delivery state
- mobile/tablet-friendly workflow UI

Decision: keep Vite SPA. Revisit Next.js only if a public SEO-heavy site or a
separate server-rendered customer portal becomes necessary.

## Routing

Use React Router for first-stage routing.

Reasons:

- widely adopted and easy for React developers to understand
- works well with Vite SPA
- supports gradual adoption
- gives URL-level expression to the core workflow
- keeps future Capacitor/mobile-shell usage simple

Initial route meanings:

```text
/                  -> Home
/projects          -> Project/Site workspace
/reports           -> Report writing workspace
/photos            -> Photo workspace
/documents         -> Document jobs/artifacts
/more              -> More/settings
/office-invitations/:token -> invitation signup
```

The route should describe the user's workflow position. It must not expose
internal backend concepts such as Flower step numbers, Agent command ids, or
storage adapter details.

Current implementation status:

- `client/web` uses `react-router` with `BrowserRouter`.
- `src/appRoutes.ts` maps top-level workflow views to URLs.
- `App.tsx` derives the active workspace view from `location.pathname`.
- Side navigation and bottom navigation now navigate by URL instead of only
  mutating local React state.
- Route-level data loading is not introduced yet. Feature APIs and TanStack
  Query remain the data layer.

## Server State

Use TanStack Query for API-backed server state:

- project lists
- site lists
- report lists
- checklist schemas and answers
- assignments
- document jobs
- artifact delivery requests
- polling status

Avoid duplicating server data in broad local state unless the feature is still
being migrated. When using mutations, invalidate or refresh the affected query.

## Forms

Use React Hook Form for real forms:

- signup
- project/site/report creation
- report step forms
- checklist answers
- template metadata
- admin configuration forms

Use Zod when form validation becomes more than required-field checks or when
request/response DTO validation is useful. Keep validation schemas close to the
feature first.

## UI System

Target UI stack:

```text
Tailwind CSS -> styling tokens/utilities
Radix UI -> accessible unstyled primitives
shadcn/ui -> owned component code copied into the repo
lucide-react -> icons
```

This is a direction, not a command to rewrite all existing CSS immediately.

Adoption rule:

1. Keep current CSS while MVP screens are still moving quickly.
2. Introduce design tokens and shared UI components first.
3. Add shadcn/Radix/Tailwind when we start standardizing buttons, inputs,
   dialogs, sheets, select menus, tabs, toasts, data tables, and mobile sheets.
4. Do not mix several UI component libraries.

## UI Workflow Layer

Do not build a universal UI engine.

Use the lightweight pattern from
`docs/architecture/UI_WORKFLOW_ORCHESTRATION.md`:

```text
Flow definition + Step registry + StepRunner + feature hooks
```

Apply it first to report writing. Extract to shared code only after the same
pattern appears in multiple real workflows such as:

- report writing
- photo capture/upload
- document generation/delivery
- approval/review

## State Machines

XState is a possible future tool, not a default dependency.

Use XState only if a UI workflow has:

- many mutually exclusive states
- complex guarded transitions
- parallel substates
- difficult retry/cancel/resume behavior
- bugs caused by impossible local state combinations

For current MVP report writing, React state plus a feature hook is enough.

## Storybook

Storybook should be introduced when shared UI components and workflow step
components begin to repeat.

Good first stories:

- project list row/card
- site list row/card
- report status row/card
- report step runner
- form step
- checklist step
- document progress panel
- empty/error/saving states

Storybook is a development workshop. It should not become product runtime code.

## Architecture Rule

Prefer feature-shaped React modules:

```text
features/<business-area>/
  api.ts
  types.ts
  hooks/
  components/
  flow/        // only when the feature has guided workflow steps
```

Keep shared UI small:

```text
components/common
shared/ui     // future, when common components are stable
```

Do not create broad abstractions before repeated patterns prove they are worth
sharing.

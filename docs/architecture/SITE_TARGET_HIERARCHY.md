# Site And Inspection Target Hierarchy

ArchDox must support many real-world inspection and supervision shapes without
hard-coding each business type into Java branches.

The core principle is:

```text
Office
-> Project
-> Site
-> Inspection Target tree
-> Inspection Report
-> Report Steps / Photos / Checklist Items
-> Document Job / Artifact
```

## Why This Matters

Construction supervision, building safety inspection, facility inspection,
asbestos inspection, and similar workflows do not all have the same physical
shape.

Examples:

- one construction site with one building
- one site with multiple buildings
- one building with multiple floors, rooms, zones, or structural elements
- one facility with equipment, machines, pipes, tanks, or utility rooms
- one report covering one target
- one report covering multiple targets
- one site producing many reports over time

The system must not model every difference as:

```java
if (reportType == SAFETY_CHECK) { ... }
if (officeId == SOME_OFFICE) { ... }
```

Instead, stable engine code should read a structured work context and resolve
templates, workflow definitions, rule sets, and output layouts from
configuration.

## Layer Definitions

### 1. Office

`office` is the tenant/security boundary.

Every site, target, report, photo, and document artifact belongs to exactly one
office either directly or through a checked parent.

### 2. Project

`project` is the largest business work container.

A project can represent:

- a customer contract
- a supervision engagement
- a safety inspection engagement
- a recurring facility management package
- a group of related sites

Rules:

- a project belongs to one office
- a project may contain one or many sites
- project-level metadata should describe the business engagement, not every
  physical building detail
- do not force one project to mean one physical location

### 3. Site

`site` is the English name for `현장`.

A site is the physical or operational place where supervision, inspection,
photos, and reports happen.

Examples:

- construction job site
- building safety inspection site
- campus/facility site
- customer branch/location
- one physical address inside a larger project

Recommended future fields:

- `id`
- `office_id`
- `project_id`
- `site_code`
- `name`
- `address`
- `site_type`
- `start_date`
- `end_date`
- `metadata_json`
- `status`

Rules:

- reports should be created under a selected project and site
- photos and document jobs should be traceable back to project and site
- a project with only one physical location still has one site
- UI should use `프로젝트` for the top container and `현장` for `site`
- the current MVP `projects` table partly covers site-like fields, but the
  intended hierarchy is `projects -> sites`

### 4. Inspection Target

`inspection_target` is the physical or logical thing being inspected,
supervised, photographed, or written into the report.

It should be tree-shaped:

```text
project
-> site
-> target: building
   -> target: floor
      -> target: room
         -> target: element
```

or:

```text
project
-> site
-> target: facility
   -> target: equipment
      -> target: component
```

Recommended future fields:

- `id`
- `office_id`
- `project_id`
- `site_id`
- `parent_target_id`
- `target_type`
- `name`
- `code`
- `address`
- `metadata_json`
- `status`

Target type examples:

- `SITE`
- `BUILDING`
- `FACILITY`
- `FLOOR`
- `ROOM`
- `ZONE`
- `STRUCTURAL_ELEMENT`
- `EQUIPMENT`
- `COMPONENT`
- `MATERIAL`
- `WORK_AREA`
- `OTHER`

Rules:

- target type controls supported UI/schema choices through configuration, not
  ad hoc branching.
- target metadata can differ by type, but tenant/security invariants stay in
  code.
- target rows are office-owned and must be checked by `office_id`.
- target trees should avoid cycles.
- Phase 6-4 introduces first-class target rows and report-target links.
- A report-target link snapshots target identity at the time of linking. This
  is important because generated documents must remain explainable even if a
  target is renamed later.

### 5. Inspection Report

`inspection_report` is the report/workflow aggregate.

Current MVP:

- report belongs to `project_id`
- detailed target selection can live in step payloads until the dedicated
  `sites` and target tables are added

Future direction:

- report belongs to one project and usually one primary site
- report references one or more inspection targets through a join table

Recommended future join table:

```text
inspection_report_targets
- id
- office_id
- report_id
- target_id
- role          # PRIMARY | SECONDARY | REFERENCE
- snapshot_json # target name/type/address/path at report creation time
```

Rules:

- a report must always carry enough site/target context for document rendering.
- target names and metadata used for generated documents should be snapshotted
  when the report/job is created, so later target edits do not rewrite history.
- a report can have one primary target and many secondary/reference targets.

Recommended future report fields or join:

- `project_id`
- `primary_site_id`
- `inspection_report_targets`

### 6. Capture Layer

Report steps, checklist rows, and photos are observations against the report and
optionally against a specific target.

Examples:

- photo attached to report only
- photo attached to checklist item
- photo attached to target `building A / 3F / machine room`
- checklist item attached to structural element or facility component

Phase 6-4 introduces first-class checklist records:

- `checklist_schemas` define reusable checklist shape.
- `checklist_items` define item code, label, answer type, requirement, and
  options.
- `inspection_checklist_answers` store per-report answers and optional
  target-specific answers.
- Document jobs snapshot report targets and checklist answers alongside steps
  and photos.

### 7. Document Output Layer

Document jobs must render from a stable snapshot:

```text
office
project
site
selected targets
report header
report steps
photos/assets
resolved template/workflow/rules/layout revisions
```

Document binding names should prefer neutral concepts:

- `site.name`
- `site.address`
- `project.name`
- `primaryTarget.name`
- `primaryTarget.type`
- `targets[]`
- `report.type`
- `report.title`
- `photos[]`

Do not bake one specific business form into the binding model.

## Configuration Resolution

Configuration can eventually resolve by:

```text
officeId
plan
reportType
targetType
workflowCode
templateCode
```

MVP resolution already uses `officeId + reportType`. Adding `targetType` later
should be a compatible extension, not a redesign.

Examples:

- Current scope: `CONSTRUCTION_DAILY_SUPERVISION_LOG + BUILDING`
- Current scope: `CONSTRUCTION_SUPERVISION_REPORT + BUILDING`
- Deferred scope example: `ASBESTOS_SURVEY + ROOM/MATERIAL`

## MVP Rule

Do not overbuild the target tree before the UI needs it.

For the next client phase:

- keep the existing `projects` table/API as the top project container for now
- introduce user-facing language that distinguishes `프로젝트` and `현장`
- if a project has no first-class `sites` row yet, create a default site concept
  in the UI/DTO flow rather than pretending the hierarchy does not exist
- require report creation to start from a selected project and site context
- keep target details in report step payloads for now
- design UI and DTO names so `sites` and `inspection_targets` can be added
  without rewriting the whole flow

When multi-building, floor/room, equipment, or multi-target reports become
needed, add `sites`, `inspection_targets`, and `inspection_report_targets` as
first-class tables.

# Domain Model

This document is the compact domain reference for implementation. The detailed
source of truth remains the numbered design documents, especially
`02_상세설계_도메인및데이터.md`.

## Office

`office` is the tenant boundary.

- Table: `offices`
- Important fields: `office_code`, `display_name`, `type`, `plan_code`, `status`
- Types:
  - `PERSONAL`: personal workspace for a personal user account.
  - `OFFICE`: architecture office/company workspace.
- Rule: most business data belongs to exactly one office.
- Rule: signup must choose one track:
  - `PERSONAL` signup creates a personal workspace and owner membership.
  - `OFFICE` signup requires `office_code + invitation_token + matching email`
    and does not create a personal workspace.
- Rule: a personal account must not silently become an office account. If a
  user needs both personal and office usage, they should be provisioned as
  separate accounts unless a later explicit account-linking design is approved.

## User

`user` is the login identity.

- Table: `users`
- Important fields: `email`, `password_hash`, `name`, `status`
- Rule: users are global identities. They access office data through
  `office_memberships`.

## Office Membership

`office_membership` connects users to offices.

- Table: `office_memberships`
- Important fields: `user_id`, `office_id`, `role`, `status`
- Roles: `OWNER`, `ADMIN`, `MEMBER`, `VIEWER`
- Rule: request-time authorization must verify active membership for the active
  office.
- Rule: office member management requires `OWNER` or `ADMIN`.
- Rule: only `OWNER` can assign `OWNER` role or modify another `OWNER`.
- Rule: an office must keep at least one active `OWNER`.
- Rule: membership deactivation sets `status = SUSPENDED`; it is not a hard
  delete.
- Rule: membership mutations must record operation events.

### MVP Office Permission Matrix

Cloud API remains the source of truth. Client UI may hide or disable actions,
but every mutating API must enforce the matching office role.

| Action area | PERSONAL | OFFICE OWNER | OFFICE ADMIN | OFFICE MEMBER | OFFICE VIEWER |
| --- | --- | --- | --- | --- | --- |
| Read office-scoped project/report data | OWNER | yes | yes | yes | yes |
| Manage office members/invitations | n/a | yes | yes, except OWNER role | no | no |
| Create/archive projects | OWNER | yes | yes | no | no |
| Create/archive sites and inspection targets | OWNER | yes | yes | if project `MANAGER` | no |
| Create reports, save steps, submit/cancel reports | OWNER | yes | yes | yes, unless assignments narrow access | no |
| Save checklist answers and attach report targets | OWNER | yes | yes | yes, unless assignments narrow access | no |

Rule: project creation/archive is office management data in the MVP, so it
requires `OWNER` or `ADMIN` in an `OFFICE` workspace. Site/target structure may
also be managed by a member explicitly assigned to the project as `MANAGER`.

Rule: report writing is normal field/operator work, so `MEMBER` can write
reports and checklist answers while no project/report assignments exist. Once
assignments are added, project/report assignment narrows the writer set.
`VIEWER` remains read-only.

## Office Invitation

`office_invitation` is the pre-membership object used to invite a signed-up or
future user into an office.

- Table: `office_invitations`
- Ownership: `office_id NOT NULL`
- Important fields: `email`, `role`, `status`, `token_hash`, `token_preview`,
  `invited_by`, `accepted_by`, `expires_at`
- Status values: `PENDING`, `ACCEPTED`, `CANCELLED`, `EXPIRED`
- Rule: office invitation management requires `OWNER` or `ADMIN`.
- Rule: only `OWNER` can create an invitation with `role = OWNER`.
- Rule: Cloud stores only the invitation token hash. The raw token is returned
  only once, in the create response.
- Rule: invite acceptance requires an authenticated user whose email matches
  the invitation email.
- Rule: accepting an invitation creates or reactivates an office membership
  using the invited role.
- Rule: email delivery is not implemented yet. The first UI copies an invite
  URL that the invitee can open after signup/login.
- Rule: invitation mutations must record operation events.

## Project

`project` is the largest business work container. It can contain one or many
physical sites.

- Table: `projects`
- Ownership: `office_id NOT NULL`
- Current important fields: `name`, `address`, `building_type`, `start_date`,
  `end_date`, `status`, `created_by`
- Status examples: `ACTIVE`, `ARCHIVED`
- Rule: a project is the top-level engagement/contract/work package, not
  necessarily one physical location.
- Rule: creating or archiving projects requires project-manager permission:
  personal `OWNER`, or office `OWNER`/`ADMIN`.
- Rule: project business type is selected from a controlled code list. The
  current API field is still named `buildingType`/`building_type`, but product
  copy should call it `업무 유형`.
- MVP business type codes: `CONSTRUCTION_SUPERVISION`,
  `BUILDING_SAFETY_INSPECTION`, `FACILITY_INSPECTION`,
  `ASBESTOS_SUPERVISION`, `MAINTENANCE_INSPECTION`, `OTHER`.
- Rule: the current MVP table still carries some site-like fields. The intended
  hierarchy is `project -> site -> inspection_target`.

## Site

`site` is the English term for `현장`.

- Future table: `sites`
- Ownership: `office_id NOT NULL`
- Parent: `project_id`
- Important fields: `site_code`, `name`, `address`, `site_type`,
  `start_date`, `end_date`, `metadata_json`, `status`
- Rule: a project may have one or many sites.
- Rule: a single-location project still has one default site concept.
- Rule: site type is selected from a controlled code list, not free text.
- MVP site type codes: `CONSTRUCTION_SITE`, `BUILDING`, `FACILITY`, `PLANT`,
  `CAMPUS`, `WORK_AREA`, `OTHER`.
- Rule: reports should be created under a selected project and site context.
- Rule: creating or archiving sites requires project-structure-manager
  permission: personal `OWNER`, office `OWNER`/`ADMIN`, or project assignment
  role `MANAGER`.
- Rule: photos, steps, document jobs, and artifacts should remain traceable to
  both the project and the site when first-class `sites` are added.

## Inspection Target

`inspection_target` is the building, floor, room, zone, facility, equipment,
component, material, or work area being inspected or documented.

- Table: `inspection_targets`
- Ownership: `office_id NOT NULL`
- Parent: `project_id`, `site_id`, optional `parent_target_id`
- Important fields: `target_type`, `name`, `code`, `address`,
  `metadata_json`, `status`
- Rule: targets are tree-shaped below a site.
- Rule: a report can reference one or more targets through
  `inspection_report_targets`.
- Rule: report-target links snapshot target name/type/address/metadata so a
  generated document remains historically explainable even if the target is
  renamed later.
- Rule: target type should select schema/template/rule behavior through
  configuration, not office-specific Java branches.
- Rule: target names and key metadata used in generated documents should be
  snapshotted for historical explainability.
- Rule: creating or archiving inspection targets requires
  project-structure-manager permission: personal `OWNER`, office
  `OWNER`/`ADMIN`, or project assignment role `MANAGER`.

## Project Assignment

`project_assignment` connects an office user to a project-level work scope.

- Table: `project_assignments`
- Ownership: `office_id NOT NULL`
- Parent: `project_id`
- Target user: `user_id`
- Roles:
  - `MANAGER`: can manage project structure such as sites and inspection
    targets, and can write reports under the project.
  - `REPORT_WRITER`: can create/write reports under the project.
  - `VIEWER`: explicit read-only project membership.
- Status values: `ACTIVE`, `REMOVED`
- Rule: assigning/removing project users requires personal `OWNER`, or office
  `OWNER`/`ADMIN`.
- Rule: when a project has no active assignments, office `MEMBER` users may
  write reports under it for MVP compatibility.
- Rule: once any active project assignment exists, office `MEMBER` report
  writing is limited to active project assignments with role `MANAGER` or
  `REPORT_WRITER`.
- Rule: project assignment events must be recorded as operation events.

## Inspection Report

`inspection_report` is the draft/submitted report aggregate.

- Table: `inspection_reports`
- Ownership: `office_id NOT NULL`
- Parent: `project_id`; future model also carries `site_id` or report-site join
- Important fields: `report_no`, `report_type`, `title`, `status`,
  `current_step`, `template_id`, `archdox_agent_id`, `content_revision`,
  `submitted_revision`, `generated_revision`, `last_document_job_id`,
  `requested_by`
- Status examples: `DRAFT`, `STEP_SAVED`, `READY_TO_GENERATE`,
  `GENERATION_REQUESTED`, `GENERATING`, `GENERATED`, `DELIVERED`, `FAILED`,
  `CANCELLED`
- Rule: a report must belong to a project in the same office and should also
  carry a site context.
- Rule: steps may be saved only in `DRAFT` or `STEP_SAVED`.
- Rule: `content_revision` is the current editable report content revision.
  `submitted_revision` records the latest revision that passed submit
  validation. `generated_revision` records the latest revision that completed
  document generation.
- Rule: clients may list saved steps to resume a wizard draft without
  reconstructing state from document jobs or artifacts.
- Rule: submit is allowed only in `DRAFT` or `STEP_SAVED` and moves the report
  to `READY_TO_GENERATE`.
- Rule: after `READY_TO_GENERATE`, `GENERATED`, `DELIVERED`, or `FAILED`, edits
  require an explicit reopen action. Reopen increments `content_revision` and
  moves the report back to `STEP_SAVED`. Prior document jobs/artifacts remain
  immutable history for their `report_revision`.
- Rule: `GENERATION_REQUESTED` and `GENERATING` are locked because a document
  snapshot is already being processed.
- Rule: cancel is allowed only before generation/output completion.
- Rule: creating reports, saving steps, submitting, and cancelling require
  report-writer permission: personal `OWNER`, or office `OWNER`/`ADMIN`/`MEMBER`.
- Rule: project and report assignments may narrow `MEMBER` write access. Report
  assignments take precedence over project assignments for a specific report.

## Report Assignment

`inspection_report_assignment` connects an office user to a specific report.

- Table: `inspection_report_assignments`
- Ownership: `office_id NOT NULL`
- Parent: `report_id`
- Target user: `user_id`
- Roles:
  - `WRITER`: can save report steps, checklist answers, report target links, and
    submit/cancel while the report lifecycle allows it.
  - `REVIEWER`: reserved for the future approval/review workflow; read-only in
    the current MVP.
  - `VIEWER`: explicit read-only report membership.
- Status values: `ACTIVE`, `REMOVED`
- Rule: assigning/removing report users requires personal `OWNER`, or office
  `OWNER`/`ADMIN`.
- Rule: when a report has active assignments, office `MEMBER` write access to
  that report requires an active `WRITER` report assignment. This intentionally
  narrows any broader project assignment.
- Rule: report assignment events must be recorded as operation events.

## Inspection Report Step

`inspection_report_step` stores per-step draft data.

- Table: `inspection_report_steps`
- Ownership: through `inspection_reports.office_id`
- Parent: `report_id`
- Important fields: `step_code`, `payload_storage_mode`, `payload_json`,
  `payload_ciphertext`, `local_draft_ref`, `payload_hash`, `client_revision`
- Storage modes:
  - `CLOUD_ENCRYPTED`: default cloud-synced draft mode
  - `LOCAL_ONLY`: detailed draft payload remains in the ArchDox Agent
- Rule: step payloads must be validated against the report template/schema as
  template support matures.
- Rule: MVP may expose a small built-in wizard step set, but long-term step
  definitions belong to configuration/template metadata, not office-specific
  Java branches.

## Checklist Schema

`checklist_schema` defines reusable checklist items for a report type and,
later, site/target-specific variants.

- Table: `checklist_schemas`
- Table: `checklist_items`
- Ownership: global rows have `office_id = null`; office-specific override rows
  may carry `office_id`.
- Important fields: `report_type`, `site_type`, `target_type`, `code`, `name`,
  `version`, `status`, `schema_json`.
- Item fields: `item_code`, `label`, `description`, `answer_type`, `required`,
  `display_order`, `options_json`.
- Rule: checklist definitions are reusable schema/configuration, not per-report
  answers.
- Rule: MVP seeds default schemas for `DAILY_SUPERVISION`, `PERIODIC_SAFETY`,
  and `FACILITY_CHECK`.
- Rule: future office-specific schemas should resolve by
  `officeId + reportType + siteType + targetType`.

## Checklist Answer

`inspection_checklist_answer` stores actual per-report checklist answers.

- Table: `inspection_checklist_answers`
- Ownership: `office_id NOT NULL`
- Parent: `report_id`, `checklist_schema_id`, `checklist_item_id`
- Optional context: `target_id`
- Important fields: `answer_value_json`, `note`, `client_revision`,
  `saved_by`, `saved_at`
- Rule: saving an answer marks the report's current step as `CHECKLIST` while
  the report is still editable.
- Rule: saving checklist answers requires report-writer permission.
- Rule: document jobs snapshot checklist answers so generated artifacts can be
  reproduced/explained from immutable job input.

## Photo

`photo` represents uploaded or locally referenced construction images.

- Table: `photos`
- Table: `photo_assets`
- Ownership: `office_id NOT NULL`
- Typical parents: project, inspection report, report step
- Important concepts:
  - `ORIGINAL` asset: temporary cloud handoff, then ArchDox Agent/NAS
  - `WORKING` asset: document generation and preview
  - `THUMBNAIL` asset: list/wizard UI preview
  - EXIF metadata
  - capture kind (`CAMERA`, `UPLOAD`)
  - upload status (`PENDING_UPLOAD`, `UPLOADED`, `DELETED`)
  - pickup status (`NOT_REQUIRED`, `PENDING`, `PICKED_UP`, `FAILED`)
  - storage location
- Rule: photo metadata belongs in cloud; actual file handling depends on the
  selected storage and delivery policy.
- Rule: `storage_ref` is a logical key, not an absolute local filesystem path.
- Rule: current MVP uses `API_LOCAL` upload instructions; production targets
  remain `S3`, `CLOUD_MEDIATED`, and `ARCHDOX_AGENT_DIRECT`.
- Rule: office original pickup completion updates the `ORIGINAL` asset to
  `AGENT_MANAGED` and records temporary original deletion.
- Rule: `PHOTO_PICKUP` command payload carries a short-lived download URL and a
  suggested agent-managed logical storage key. The ArchDox Agent stores the
  original under its configured storage profile and reports the logical key back
  to Cloud.
- Rule: `PHOTO_PICKUP` retry/backoff and timeout handling belongs to the
  `photo-pickup` Flower flow. Cloud refreshes the download URL by creating a
  fresh command for each attempt and marks pickup `FAILED` only after the flow
  retry budget is exhausted.
- Rule: `PhotoUploadConfirmed` is a Bloom event. It starts the
  `photo-derivative-generation` Flower flow and, when required, the
  `photo-pickup` Flower flow.
- Rule: derivative generation creates `WORKING` and `THUMBNAIL` assets
  asynchronously. `WORKING` uses a 2048px long edge default, and `THUMBNAIL`
  uses WebP with a 512px long edge default.

## Template

`template` defines the report form, DOCX binding, validation schema, and optional
AI prompts.

- Tables: `document_templates`, `document_template_revisions`,
  `document_types`
- Ownership:
  - `office_id NULL`: system default template
  - `office_id NOT NULL`: office-specific override/custom template
- Important fields: `template_code`, `version`, `schema_json`,
  `compose_policy_json`, `ai_prompts_json`, `edit_status`
- Rule: templates are versioned. Existing reports should keep using the version
  selected at creation/generation time.
- Rule: templates are the first customization layer for office-specific
  document wording and layout. Java code must not branch on `officeId` to build
  different office-specific documents.
- Rule: document generation should bind data into the selected template
  revision through `document-engine`.
- Rule: Template Binding V1 supports DOCX templates whose XML contains `${...}`
  placeholders. The engine binds values from the immutable document job snapshot
  and leaves unresolved placeholders visible.
- Rule: DOCX Placeholder Hardening V1 supports placeholders split across
  multiple Word text nodes, such as `${pro` + `ject` + `Name}`. The resolved
  value is written into the first involved text node while the remaining
  placeholder fragments are cleared.
- Rule: template revision `schema.bindings` may map business placeholder names
  to snapshot paths. Example: `projectName -> project.name`,
  `inspectionDate -> steps.BASIC_INFO.payload.inspectionDate`. Resolved values
  are stored under `document_jobs.input_snapshot_json.templateFields`.
- Rule: when the selected template file is not available in configured document
  storage, Cloud generation falls back to the simple generated DOCX path.
- Rule: office admins upload DOCX content to `DRAFT` template revisions through
  the Template Upload API. The API stores the file and assigns the storage
  reference; clients should not handcraft object-store paths.
- Rule: published template revision content is immutable. A template file change
  creates a new revision, then an office override can point future jobs to that
  revision.

## Workflow Definition

`workflow_definition` describes the business stages required for a report or
document workflow.

- Tables: `workflow_definitions`,
  `workflow_definition_revisions`
- Ownership:
  - `office_id NULL`: system or plan/report-type default workflow
  - `office_id NOT NULL`: office-specific workflow
- Important fields: `workflow_code`, `report_type`, `version`,
  `definition_json`, `status`
- Rule: workflow definitions are versioned. In-progress reports/jobs keep the
  selected workflow revision.
- Rule: Flower remains the execution engine. A workflow definition may select or
  parameterize supported stages, but must not execute arbitrary code.
- Rule: retry, timeout, waiting, and backoff remain Flower responsibilities.

## Rule Set

`rule_set` stores configurable validation and business requirements.

- Tables: `rule_sets`, `rule_set_revisions`
- Ownership:
  - `office_id NULL`: system or plan/report-type default rules
  - `office_id NOT NULL`: office-specific rules
- Important fields: `rule_set_code`, `report_type`, `version`, `rules_json`,
  `status`
- Examples: required fields, minimum photo count, required approval roles,
  checklist requirements by report type.
- Rule: rule sets can describe customer-specific validation, but tenant
  isolation, authorization, and security invariants must remain in code.

## Output Layout Config

`output_layout_config` describes document output layout choices that customers
often change.

- Tables: `output_layout_configs`,
  `output_layout_config_revisions`
- Ownership:
  - `office_id NULL`: system or plan/report-type default layout
  - `office_id NOT NULL`: office-specific layout
- Important fields: `layout_code`, `report_type`, `version`,
  `layout_json`, `status`
- Examples: photo table shape, section order, repeated fields, optional blocks,
  labels.
- Rule: keep the layout DSL narrow and explicit. It should configure supported
  document layout behavior, not become a general programming language.
- Rule: Output Layout V1 supports text-block sections. `payload.sections[*].key`
  becomes a `templateFields` placeholder, so a DOCX template can use
  `${photoSection}` or `${checklistSection}`.
- Rule: `PHOTO_TABLE` is the first rich DOCX layout. When the matching
  placeholder is a standalone Word paragraph, `document-engine` can replace it
  with a generated Word table and embedded working-image media entries.
- Rule: `PHOTO_TABLE` layout options stay intentionally small: `photosPerRow`
  controls detail table versus grid layout, `imageSize` controls embedded image
  display size, and `fields` controls the visible photo metadata rows.
- Rule: rich DOCX tables may use narrow polish options such as `includeTitle`,
  `emptyText`, `tableStyle`, `borderColor`, `headerFill`, `titleFill`, and
  explicit column widths. These options improve output quality while keeping
  layout configuration bounded.
- Rule: `CHECKLIST_TABLE` is the checklist rich DOCX layout. When the matching
  placeholder is a standalone Word paragraph, `document-engine` can replace it
  with a generated Word table from the saved checklist answer snapshot.
- Rule: `CHECKLIST_TABLE` remains narrow: `fields` controls visible columns and
  may read known checklist answer paths such as `itemCode`, `label`,
  `answer.value`, and `note`.
- Rule: document-engine changes that affect DOCX binding, rich tables, or media
  embedding should keep a realistic smoke test passing. The smoke test must
  cover normal placeholders, split Word text placeholders, photo media
  relationships, and checklist table rendering together.
- Rule: HWP/HWPX and PDF are delivery/export artifact formats. The business
  source of truth remains the report snapshot plus versioned template/layout
  configuration. DOCX is the first render substrate until a reliable
  HWP/HWPX renderer or converter is selected.

## Office Config Override

`office_config_override` maps an office/report type to specific configuration
revisions.

- Table: `office_config_overrides`
- Ownership: `office_id NOT NULL`
- Important fields: `office_id`, `report_type`, `template_revision_id`,
  `workflow_revision_id`, `rule_set_revision_id`, `layout_revision_id`,
  `status`, `effective_from`, `effective_to`
- Rule: configuration resolution order is office override, then
  plan/report-type default, then system default.
- Rule: resolved configuration must be explainable and should be snapshotted or
  referenced by immutable revision id when a report/job is created.

## Document Job

`document_job` tracks document generation.

- Table: `document_jobs`
- Ownership: `office_id NOT NULL`
- Parent: usually `inspection_report`
- Important fields: `report_id`, `template_id`, `status`, `requested_by`,
  `report_revision`, `progress_step`, `progress_percent`, `progress_message`,
  `worker_type`, `output_format`, `input_snapshot_json`, `error_code`,
  `error_message`, `requested_at`, `started_at`, `completed_at`
- Status values: `REQUESTED`, `GENERATING`, `GENERATED`, `FAILED`, `CANCELLED`
- Progress step values: `QUEUED`, `VALIDATING`, `DISPATCHING`,
  `WAITING_FOR_AGENT`, `RENDERING`, `STORING_ARTIFACTS`, `GENERATED`, `FAILED`
- Worker types:
  - `CLOUD`: generated by a cloud document worker. Current MVP may run this
    inside Cloud API, but the target is an ArchDox Agent process with
    `deploymentMode=CLOUD_MANAGED`.
  - `ARCHDOX_AGENT`: generated by an office-ArchDox Agent through the WebSocket
    command channel.
- Output formats: `DOCX`, `HTML`, `HTML_AND_PDF`, `PDF`, `DOCX_AND_PDF`,
  `HWP`, `HWPX`
- Rule: job creation belongs to `cloud-api`; reusable composition belongs to
  `document-engine`; execution belongs to `archdox-agent`.
- Rule: Phase 4 MVP supports `CLOUD + DOCX`. `ARCHDOX_AGENT` rendering and PDF
  conversion remain explicit follow-up phases.
- Rule: `DOCX` is the first render substrate. `HTML`, `PDF`, `HWP`, and `HWPX`
  are artifact/export targets behind `DocumentArtifactExporter` until a native
  renderer is intentionally added.
- Rule: missing export infrastructure must fail with a stable error code such
  as `DOCUMENT_EXPORTER_NOT_CONFIGURED`, not with an ambiguous render failure.
- Rule: Phase 4-3 introduces `DocumentRenderFlow` as the common asynchronous
  render flow for both office and personal plans.
- Rule: office plans should prefer `ARCHDOX_AGENT` when an authenticated
  `LOCAL_OFFICE` agent is installed and online. Personal plans should use
  `CLOUD`; later this may route to a `CLOUD_MANAGED` ArchDox Agent process.
- Rule: `DocumentRenderFlow` should route by worker type, but the persisted job
  contract should stay the same. UI polling must not care whether rendering was
  done by a `LOCAL_OFFICE` or `CLOUD_MANAGED` ArchDox Agent.
- Rule: ArchDox Agent rendering is started by a `GENERATE_DOCUMENT` command. ACK,
  completion, and failure are reported back over the Cloud WebSocket channel and
  converted into Flower/Bloom events for the waiting render step.
- Rule: `GENERATE_DOCUMENT` carries the selected template metadata and, when
  Cloud has the template content, an agent-authenticated template download URL.
  The ArchDox Agent uses the shared `document-engine` module and may cache
  immutable template revision content by logical `storageRef`.
- Rule: `status` is the coarse lifecycle state. `progress_step`,
  `progress_percent`, and `progress_message` are the UI polling state.
- Rule: the create API returns immediately after persisting the job and
  submitting the Flower flow. The web client should poll
  `GET /api/v1/document-jobs/{jobId}` until `GENERATED` or `FAILED`.
- Rule: every job stores `input_snapshot_json` so generated documents are
  explainable even if report steps or photos change later.
- Rule: every job stores `report_revision`; document artifacts produced by the
  job must be interpreted as output for that exact report revision.
- Rule: job creation resolves the current office/report configuration and stores
  it under `input_snapshot_json.configuration`. Rendering must use that snapshot
  rather than re-resolving the latest template/config.
- Rule: during the compatibility phase, `document_jobs.template_id` stores the
  selected document template revision id when one is resolved. Future schema may
  split this into explicit `template_revision_id`, `workflow_revision_id`,
  `rule_set_revision_id`, and `output_layout_revision_id` columns.
- Rule: creating a job moves the report through `GENERATION_REQUESTED` and
  `GENERATING`; successful completion moves it to `GENERATED`.
- Rule: the document generation REST entrypoint submits a
  `document-generation` Flower flow after job creation. The flow input is
  `DocumentGenerationRequested`, but it is a flow command object here, not a
  Bloom-published start event.
- Rule: document generation steps use stepNo-based async execution and retry:
  `0` submit, `10` observe, `20` backoff.
- Rule: successful completion publishes `DocumentGeneratedEvent` through Bloom.
- Rule: retry exhaustion publishes `DocumentGenerationFailedEvent`.

## Document Artifact

`document_artifact` is an output file from a document job.

- Table: `document_artifacts`
- Ownership: `office_id NOT NULL`
- Parent: `document_job`
- Types: `DOCX`, `HTML`, `PDF`, `HWP`, `HWPX`, `PRINT_LOG`
- Storage kinds: `API_LOCAL`, `S3`, `ARCHDOX_AGENT`
- Rule: artifact metadata lives in cloud. Binary storage location must follow the
  storage and delivery policy.
- Rule: `API_LOCAL` artifacts can be downloaded through Cloud API after office
  authorization.
- Rule: `ARCHDOX_AGENT` artifacts cannot be directly downloaded through Cloud API
  because the binary lives in ArchDox Agent/NAS storage. A delivery flow must ask
  the ArchDox Agent to upload/share/prepare the file.

## Document Delivery Request

`document_delivery_request` tracks a requested delivery/share action for a
generated artifact.

- Table: `document_delivery_requests`
- Ownership: `office_id NOT NULL`
- Parent: `document_job`; optionally one `document_artifact`
- Channels: `DOWNLOAD`, `EMAIL`, `KAKAO`, `EXTERNAL_STORAGE`
- Status values: `REQUESTED`, `SENDING`, `COMPLETED`, `FAILED`, `CANCELLED`
- Rule: Phase 4-4 implements `DOWNLOAD` delivery request APIs.
- Rule: Phase 4-6 implements `DOWNLOAD + ARCHDOX_AGENT` artifact preparation
  through the `document-delivery` Flower flow. The flow sends
  `UPLOAD_DOCUMENT_ARTIFACT`, waits for ACK/completion/failure events, and owns
  retry/backoff.
- Rule: `DOWNLOAD + API_LOCAL` can complete immediately and returns a direct
  Cloud download URL.
- Rule: `DOWNLOAD + ARCHDOX_AGENT` records a `SENDING` request while the Agent
  uploads a prepared Cloud copy. When upload succeeds, the request stores
  `prepared_storage_kind`, `prepared_storage_ref`, `prepared_expires_at`,
  `download_ready_at`, and `agent_command_id`, then becomes `COMPLETED`.

## ArchDox Agent

`archdox_agent` is the registered execution runtime for document/photo work.
It is not inherently local. Locality is a deployment property.

- Tables: `archdox_agents`, `archdox_agent_install_tokens`,
  `archdox_agent_heartbeats`, `archdox_agent_commands`,
  `archdox_agent_sessions`
- Ownership: `office_id NOT NULL`
- Deployment modes:
  - `LOCAL_OFFICE`: office-installed runtime, usually backed by NAS/local disk.
  - `CLOUD_MANAGED`: managed cloud runtime, usually backed by S3-compatible
    storage.
- Storage profile: `storage_profile_json` reports original/working/artifact
  storage targets. Cloud must treat agent-managed storage refs as logical refs.
- Responsibilities:
  - connect outbound to Cloud API through WebSocket
  - receive commands
  - execute `PHOTO_PICKUP` by downloading the temporary original and storing it
    in NAS/agent storage
  - execute `GENERATE_DOCUMENT` by running `document-engine` and reporting
    artifact metadata
  - execute `UPLOAD_DOCUMENT_ARTIFACT` by reading an agent-managed document
    artifact and uploading a prepared Cloud delivery copy
  - report heartbeat and command result
  - preserve `LOCAL_ONLY` draft payloads when configured
- Rule: the ArchDox Agent must never access another office's data.
- Rule: production-style pairing uses one-time install tokens. Cloud stores
  install token hashes only.
- Rule: after pairing, the ArchDox Agent authenticates with `agentId` and an
  agent-specific device secret. Cloud stores the device secret hash only.
- Rule: shared-secret authentication is a development fallback, not the
  production office trust model.
- Rule: WebSocket is for command/status messages, not default large-file
  transfer.
- Rule: `PHOTO_PICKUP` completion is reported as a command event. The
  `photo-pickup` Flower flow applies the business completion and moves the
  original photo asset to `AGENT_MANAGED`.
- Rule: `archdox_agent_commands` tracks command delivery state. Business
  retry/backoff for photo pickup, document render, and document delivery belongs
  to their Flower flows, not to a Spring scheduler.
- Rule: in multi-API-instance deployments, Agent WebSocket sessions are
  transport state only. `archdox_agent_commands` remains the command source of
  truth.
- Rule: `archdox_agent_sessions` records `api_instance_id`,
  `websocket_session_id`, ACTIVE/DISCONNECTED state, and `last_seen_at` so Cloud
  API can prefer currently connected Agents without treating API memory as the
  source of truth.

## Cross-Cutting Rules

- `office_id` is the most important business boundary.
- Global reference data must be explicitly documented as global.
- System default rows that use `office_id NULL` must be read-only or carefully
  overridden by office-specific rows.
- Generated documents should use immutable input snapshots so regeneration is
  explainable.
- Module-internal domain events should use Bloom.
- Long-running orchestration, retries, timeouts, and step-by-step workflows
  should use Flower.

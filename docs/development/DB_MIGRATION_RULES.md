# DB Migration Rules

ArchDox uses Flyway migrations under
`cloud-api/src/main/resources/db/migration`.

## Version Rules

1. Migration names must use Flyway's versioned format:
   - `V1__init_auth_tenancy.sql`
   - `V2__projects_and_inspections.sql`
   - `V8__document_jobs_and_artifacts.sql`
   - `V15__sites_and_report_site_context.sql`
2. Never edit a migration that may already have been applied outside the local
   workspace. Add a new migration instead.
3. Keep each migration focused on one logical feature group.
4. SQL must be PostgreSQL-compatible.
5. Do not rely on Hibernate auto-DDL for schema changes.
6. If a migration creates tables and the Java entities in the same change, test
   the module with Flyway enabled.

## Table Ownership Rules

Agent-related tables must use the official `archdox_agent_*` naming:
`archdox_agents`, `archdox_agent_install_tokens`,
`archdox_agent_heartbeats`, `archdox_agent_commands`, and the future
`archdox_agent_sessions`. Do not introduce `local_agent_*` tables or columns;
locality is represented by `archdox_agents.deployment_mode`.

`archdox_agent_sessions` is operational routing state for multi-API-instance
deployments. It should include `office_id`, `agent_id`, `api_instance_id`,
`websocket_session_id`, lifecycle timestamps, and `status`. It must not replace
`archdox_agent_commands` as the durable command source of truth.

Every new table must be classified before implementation:

1. Office-owned business table: must include `office_id BIGINT NOT NULL`.
2. Child table owned through parent: may omit `office_id` only when every access
   path verifies the parent office. Example: `inspection_report_steps` is owned
   through `inspection_reports`.
3. Global identity/reference table: may omit `office_id`. Example: `users`,
   `offices`, `feature_codes`, `plans`.
4. System-default/customizable table: may allow `office_id NULL`, where `NULL`
   means platform default and non-null means office override. Example:
   `document_templates`, `workflow_definitions`, `rule_sets`,
   `output_layout_configs`.
5. Audit/log table: may allow nullable `office_id` only when system-level events
   are expected.

If classification is unclear, stop and document the decision before writing SQL.

## Required Columns

Office-owned mutable business tables should normally include:

- `id bigserial primary key`
- `office_id bigint not null references offices(id)`
- business fields
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

Rows created by users should include one of:

- `created_by bigint references users(id)`
- `requested_by bigint references users(id)`
- `saved_by bigint references users(id)`

Status-bearing tables should use a `text not null` status column and a matching
Java enum.

`sites` is an office-owned business table. It belongs to `projects` and should
keep its own `office_id` because site lists and site detail reads are
office-scoped user workflows.

## Index Rules

1. Add an index for common office-scoped list queries:
   - `(office_id, status)`
   - `(office_id, status, updated_at desc)`
   - `(office_id, created_at desc)`
2. Add parent lookup indexes for child tables:
   - `(project_id)`
   - `(report_id)`
   - `(document_job_id)`
3. Uniqueness must include `office_id` when values are only unique per office:
   - `unique (office_id, project_id, report_no)`
   - `unique (office_id, template_code, version)`
4. Avoid globally unique business codes unless the design explicitly requires
   global uniqueness.
5. Versioned customizable tables should usually have uniqueness shaped like:
   - active definition: `(office_id, code)` or `(office_id, report_type, code)`
   - immutable revision: `(definition_id, version)`
   Use a partial unique index when only one published/active override is allowed
   for a given office/report type.

## Foreign Key Rules

1. Reference parent tables explicitly.
2. For office-owned child rows, enforce office consistency in service code even
   when SQL cannot express a composite FK yet.
3. Do not cascade-delete business history unless approved.
4. Prefer soft lifecycle states for user-visible data.

## JSON And Encryption Rules

1. `jsonb` is allowed for schema-driven drafts, template schemas, snapshots, and
   provider metadata.
2. JSON fields must have a documented owner and expected shape.
3. Sensitive draft payloads must follow `payload_storage_mode`:
   - `CLOUD_ENCRYPTED`: cloud stores recoverable encrypted payload.
   - `LOCAL_ONLY`: cloud stores metadata/hash/reference only.
4. Until encryption is implemented, code must clearly state when JSON is a
   temporary MVP storage path.
5. Configuration JSON must have a documented owner and versioned shape. Do not
   store arbitrary executable expressions, raw SQL, unrestricted service calls,
   or security decisions in JSON config.
6. Generated document jobs should snapshot or reference immutable revisions for
   template, workflow, rule set, and output layout configuration.

## Migration Checklist

Before committing a migration:

1. Confirm table ownership and `office_id` decision.
2. Confirm indexes for list/read paths.
3. Confirm unique constraints include tenant scope when needed.
4. Confirm Java entities and repositories match SQL names.
5. Run the relevant Gradle tests.

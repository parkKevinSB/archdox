create table inspection_targets (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    project_id bigint not null references projects(id),
    site_id bigint not null references sites(id),
    parent_target_id bigint references inspection_targets(id),
    target_type text not null,
    code text,
    name text not null,
    address text,
    metadata_json jsonb not null default '{}'::jsonb,
    status text not null,
    created_by bigint references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index ix_inspection_targets_office_site_status
    on inspection_targets (office_id, site_id, status);
create index ix_inspection_targets_parent
    on inspection_targets (parent_target_id);

create table inspection_report_targets (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    report_id bigint not null references inspection_reports(id),
    target_id bigint not null references inspection_targets(id),
    role text not null,
    snapshot_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    unique (report_id, target_id, role)
);

create index ix_inspection_report_targets_report
    on inspection_report_targets (report_id, role);

create table checklist_schemas (
    id bigserial primary key,
    office_id bigint references offices(id),
    report_type text not null,
    site_type text,
    target_type text,
    code text not null,
    name text not null,
    version integer not null,
    status text not null,
    schema_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index ix_checklist_schemas_resolution
    on checklist_schemas (office_id, report_type, site_type, target_type, status);

create table checklist_items (
    id bigserial primary key,
    checklist_schema_id bigint not null references checklist_schemas(id),
    item_code text not null,
    label text not null,
    description text,
    answer_type text not null,
    required boolean not null default false,
    display_order integer not null,
    options_json jsonb not null default '[]'::jsonb,
    unique (checklist_schema_id, item_code)
);

create index ix_checklist_items_schema_order
    on checklist_items (checklist_schema_id, display_order);

create table inspection_checklist_answers (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    report_id bigint not null references inspection_reports(id),
    checklist_schema_id bigint not null references checklist_schemas(id),
    checklist_item_id bigint not null references checklist_items(id),
    target_id bigint references inspection_targets(id),
    answer_value_json jsonb not null default '{}'::jsonb,
    note text,
    client_revision integer not null default 1,
    saved_by bigint references users(id),
    saved_at timestamptz not null,
    unique (report_id, checklist_item_id, target_id)
);

create index ix_inspection_checklist_answers_report
    on inspection_checklist_answers (report_id, checklist_schema_id);

-- V16 intentionally creates only shared target/checklist tables.
-- Built-in document/checklist packs are seeded by focused domain migrations.

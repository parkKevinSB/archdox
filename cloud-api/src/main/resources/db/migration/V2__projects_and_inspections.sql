create table projects (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    name text not null,
    address text,
    building_type text,
    start_date date,
    end_date date,
    status text not null,
    created_by bigint references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index ix_projects_office_status on projects (office_id, status);

create table inspection_reports (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    project_id bigint not null references projects(id),
    report_no text not null,
    report_type text not null,
    title text,
    status text not null,
    current_step text,
    template_id bigint,
    archdox_agent_id bigint,
    requested_by bigint references users(id),
    generated_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (office_id, project_id, report_no)
);

create index ix_inspection_reports_office_status_updated on inspection_reports (office_id, status, updated_at desc);
create index ix_inspection_reports_project on inspection_reports (project_id);

create table inspection_report_steps (
    id bigserial primary key,
    report_id bigint not null references inspection_reports(id),
    step_code text not null,
    payload_storage_mode text not null default 'CLOUD_ENCRYPTED',
    payload_json jsonb,
    payload_ciphertext bytea,
    local_draft_ref text,
    payload_hash bytea,
    client_revision integer not null default 0,
    saved_by bigint references users(id),
    saved_at timestamptz not null,
    unique (report_id, step_code)
);

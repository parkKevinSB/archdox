create table project_assignments (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    project_id bigint not null references projects(id),
    user_id bigint not null references users(id),
    role text not null,
    status text not null,
    assigned_by bigint references users(id),
    assigned_at timestamptz not null,
    updated_at timestamptz not null,
    unique (office_id, project_id, user_id)
);

create index ix_project_assignments_project_status
    on project_assignments (office_id, project_id, status);
create index ix_project_assignments_user_status
    on project_assignments (office_id, user_id, status);

create table inspection_report_assignments (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    report_id bigint not null references inspection_reports(id),
    user_id bigint not null references users(id),
    role text not null,
    status text not null,
    assigned_by bigint references users(id),
    assigned_at timestamptz not null,
    updated_at timestamptz not null,
    unique (office_id, report_id, user_id)
);

create index ix_inspection_report_assignments_report_status
    on inspection_report_assignments (office_id, report_id, status);
create index ix_inspection_report_assignments_user_status
    on inspection_report_assignments (office_id, user_id, status);

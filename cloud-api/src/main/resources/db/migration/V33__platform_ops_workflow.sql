create table platform_ops_runs (
    id bigserial primary key,
    trigger_type varchar(80) not null,
    status varchar(40) not null,
    started_by_user_id bigint references users(id),
    incident_id bigint,
    input_snapshot_json jsonb not null default '{}'::jsonb,
    ai_harness_run_id varchar(120),
    started_at timestamptz not null,
    completed_at timestamptz,
    failure_code varchar(120)
);

create index idx_platform_ops_runs_started
    on platform_ops_runs (started_at desc);

create index idx_platform_ops_runs_status_started
    on platform_ops_runs (status, started_at desc);

create table platform_ops_incidents (
    id bigserial primary key,
    status varchar(40) not null,
    severity varchar(40) not null,
    category varchar(80) not null,
    title varchar(240) not null,
    summary text not null,
    office_id bigint references offices(id),
    primary_resource_type varchar(80),
    primary_resource_id varchar(120),
    first_seen_at timestamptz not null,
    last_seen_at timestamptz not null,
    resolved_at timestamptz,
    created_by_run_id bigint
);

create index idx_platform_ops_incidents_status_seen
    on platform_ops_incidents (status, last_seen_at desc);

create index idx_platform_ops_incidents_office_status
    on platform_ops_incidents (office_id, status, last_seen_at desc);

create index idx_platform_ops_incidents_resource
    on platform_ops_incidents (category, primary_resource_type, primary_resource_id, status);

create table platform_ops_findings (
    id bigserial primary key,
    incident_id bigint references platform_ops_incidents(id),
    run_id bigint not null references platform_ops_runs(id),
    office_id bigint references offices(id),
    severity varchar(40) not null,
    source varchar(40) not null,
    code varchar(120) not null,
    category varchar(80) not null,
    title varchar(240) not null,
    message text not null,
    resource_type varchar(80),
    resource_id varchar(120),
    workflow_type varchar(80),
    workflow_key varchar(120),
    evidence_json jsonb not null default '{}'::jsonb,
    recommendation text,
    created_at timestamptz not null
);

create index idx_platform_ops_findings_run
    on platform_ops_findings (run_id, id);

create index idx_platform_ops_findings_incident
    on platform_ops_findings (incident_id, created_at desc);

create index idx_platform_ops_findings_office_created
    on platform_ops_findings (office_id, created_at desc);

create index idx_platform_ops_findings_severity_created
    on platform_ops_findings (severity, created_at desc);

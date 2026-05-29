create table report_preflight_review_runs (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    report_id bigint not null references inspection_reports(id) on delete cascade,
    report_revision integer not null,
    status varchar(40) not null,
    requested_by bigint references users(id),
    terminal_reason text,
    requested_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz
);

create index idx_report_preflight_review_runs_report
    on report_preflight_review_runs (office_id, report_id, requested_at desc);

create index idx_report_preflight_review_runs_status
    on report_preflight_review_runs (status, updated_at);

create table report_preflight_review_findings (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    review_run_id bigint not null references report_preflight_review_runs(id) on delete cascade,
    report_id bigint not null references inspection_reports(id) on delete cascade,
    source varchar(40) not null,
    code varchar(120) not null,
    severity varchar(40) not null,
    location varchar(255),
    message text not null,
    evidence text,
    attributes_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null
);

create index idx_report_preflight_review_findings_run
    on report_preflight_review_findings (office_id, review_run_id, id);

create index idx_report_preflight_review_findings_report
    on report_preflight_review_findings (office_id, report_id);

create table document_ai_review_runs (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    document_job_id bigint not null references document_jobs(id) on delete cascade,
    report_id bigint not null references inspection_reports(id) on delete cascade,
    harness_run_id varchar(80) not null unique,
    harness_id varchar(120) not null,
    prompt_id varchar(120) not null,
    prompt_version varchar(80) not null,
    status varchar(40) not null,
    attempt integer not null default 0,
    current_call_id varchar(160),
    terminal_reason text,
    requested_by bigint references users(id),
    requested_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz
);

create index idx_document_ai_review_runs_office_job
    on document_ai_review_runs (office_id, document_job_id, requested_at desc);

create index idx_document_ai_review_runs_status
    on document_ai_review_runs (status, updated_at);

create table document_ai_review_findings (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    review_run_id bigint not null references document_ai_review_runs(id) on delete cascade,
    document_job_id bigint not null references document_jobs(id) on delete cascade,
    report_id bigint not null references inspection_reports(id) on delete cascade,
    code varchar(120) not null,
    severity varchar(40) not null,
    location varchar(255),
    message text not null,
    evidence text,
    attributes_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null
);

create index idx_document_ai_review_findings_run
    on document_ai_review_findings (office_id, review_run_id, id);

create index idx_document_ai_review_findings_job
    on document_ai_review_findings (office_id, document_job_id);

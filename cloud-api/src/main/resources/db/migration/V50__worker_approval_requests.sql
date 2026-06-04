create table if not exists worker_approval_requests (
    id bigserial primary key,
    office_id bigint,
    worker_request_id uuid not null,
    request_source varchar(40) not null,
    command text,
    user_id bigint,
    project_id bigint,
    site_id bigint,
    report_id bigint,
    document_job_id bigint,
    locale varchar(40) not null default 'ko-KR',
    action_type varchar(80) not null,
    action_origin varchar(40) not null,
    action_reason text,
    confidence numeric(5, 4) not null default 1.0,
    action_payload_json jsonb not null default '{}'::jsonb,
    decision_code varchar(120),
    decision_message text,
    status varchar(40) not null,
    requested_at timestamptz not null,
    expires_at timestamptz,
    decided_by_user_id bigint,
    decision_reason text,
    decided_at timestamptz,
    execution_request_id uuid,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index if not exists ux_worker_approval_request_worker_action
    on worker_approval_requests (worker_request_id, action_type);

create index if not exists ix_worker_approval_requests_status_requested
    on worker_approval_requests (status, requested_at desc);

create index if not exists ix_worker_approval_requests_office_status
    on worker_approval_requests (office_id, status, requested_at desc);

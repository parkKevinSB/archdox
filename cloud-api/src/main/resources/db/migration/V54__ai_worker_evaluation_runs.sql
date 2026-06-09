create table ai_worker_evaluation_runs (
    id bigserial primary key,
    run_key varchar(80) not null unique,
    trigger_type varchar(80) not null,
    status varchar(32) not null,
    evaluation_mode varchar(80) not null,
    total_cases integer not null,
    automated_cases integer not null,
    passed_cases integer not null,
    warning_cases integer not null,
    failed_cases integer not null,
    pass_rate_percent integer not null,
    group_count integer not null,
    signal_count integer not null,
    warning_signal_count integer not null,
    failed_signal_count integer not null,
    summary_json jsonb not null default '{}'::jsonb,
    triggered_by_user_id bigint,
    triggered_by_email varchar(255),
    created_at timestamptz not null,
    completed_at timestamptz not null
);

create index idx_ai_worker_evaluation_runs_created_at
    on ai_worker_evaluation_runs (created_at desc, id desc);

create index idx_ai_worker_evaluation_runs_status
    on ai_worker_evaluation_runs (status, created_at desc);

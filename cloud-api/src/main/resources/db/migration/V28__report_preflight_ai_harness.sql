alter table report_preflight_review_runs
    add column harness_run_id varchar(120),
    add column harness_id varchar(160),
    add column prompt_id varchar(160),
    add column prompt_version varchar(80),
    add column harness_status varchar(60),
    add column harness_attempt integer not null default 0,
    add column harness_current_call_id varchar(160),
    add column harness_terminal_reason text,
    add column ai_provider_code varchar(120),
    add column ai_model_id varchar(240);

create unique index idx_report_preflight_review_runs_harness_run
    on report_preflight_review_runs (harness_run_id)
    where harness_run_id is not null;

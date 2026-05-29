create table ai_harness_trace_events (
    id bigserial primary key,
    office_id bigint references offices(id),
    harness_run_id varchar(160) not null,
    harness_id varchar(160) not null,
    event_type varchar(80) not null,
    status varchar(40),
    attempt integer,
    model_id varchar(240),
    call_id varchar(160),
    prompt_id varchar(160),
    prompt_version varchar(80),
    input_tokens integer,
    output_tokens integer,
    latency_ms bigint,
    finding_count integer,
    validation_valid boolean,
    validation_error_count integer,
    error_type varchar(160),
    message text,
    attributes_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null
);

create index idx_ai_harness_trace_events_run_created
    on ai_harness_trace_events (harness_run_id, created_at asc, id asc);

create index idx_ai_harness_trace_events_created
    on ai_harness_trace_events (created_at desc, id desc);

create index idx_ai_harness_trace_events_harness_created
    on ai_harness_trace_events (harness_id, created_at desc, id desc);

create index idx_ai_harness_trace_events_event_created
    on ai_harness_trace_events (event_type, created_at desc, id desc);

create index idx_ai_harness_trace_events_office_created
    on ai_harness_trace_events (office_id, created_at desc, id desc);

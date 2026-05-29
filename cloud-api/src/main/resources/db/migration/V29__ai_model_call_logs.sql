create table ai_model_call_logs (
    id bigserial primary key,
    call_id varchar(160) not null,
    office_id bigint references offices(id),
    provider_credential_id bigint references ai_provider_credentials(id),
    provider_code varchar(120) not null,
    provider_type varchar(40) not null,
    model_id varchar(240) not null,
    model_name varchar(160) not null,
    feature varchar(80),
    workflow_type varchar(120),
    workflow_key varchar(240),
    resource_type varchar(120),
    resource_id varchar(120),
    status varchar(40) not null,
    input_tokens integer,
    output_tokens integer,
    latency_ms bigint,
    finish_reason varchar(80),
    provider_response_id varchar(160),
    error_type varchar(160),
    error_message text,
    requested_at timestamptz not null,
    completed_at timestamptz not null
);

create index idx_ai_model_call_logs_completed_at
    on ai_model_call_logs (completed_at desc);

create index idx_ai_model_call_logs_office_completed_at
    on ai_model_call_logs (office_id, completed_at desc);

create index idx_ai_model_call_logs_status_completed_at
    on ai_model_call_logs (status, completed_at desc);

create index idx_ai_model_call_logs_provider_completed_at
    on ai_model_call_logs (provider_code, completed_at desc);

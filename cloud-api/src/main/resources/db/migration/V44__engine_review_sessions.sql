create table engine_review_sessions (
    id bigserial primary key,
    external_session_id varchar(120) not null unique,
    owner_user_id bigint not null references users(id),
    office_id bigint references offices(id),
    customer_project_ref varchar(240),
    review_purpose varchar(120) not null,
    status varchar(60) not null,
    document_type_hint varchar(160),
    file_name varchar(500),
    document_text text,
    facts_json jsonb not null default '{"facts":[]}'::jsonb,
    normalized_context_json jsonb not null default '{}'::jsonb,
    validation_result_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    normalized_at timestamptz,
    completed_at timestamptz
);

create index idx_engine_review_sessions_owner_created
    on engine_review_sessions (owner_user_id, created_at desc, id desc);

create index idx_engine_review_sessions_status_updated
    on engine_review_sessions (status, updated_at desc, id desc);

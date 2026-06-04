create table engine_api_usage_events (
    id bigserial primary key,
    api_key_id bigint not null references engine_api_keys(id),
    key_id varchar(80) not null,
    owner_user_id bigint not null references users(id),
    office_id bigint references offices(id),
    capability varchar(120) not null,
    operation varchar(120) not null,
    review_session_id varchar(120),
    status varchar(40) not null,
    request_units integer not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null
);

create index idx_engine_api_usage_events_key_created
    on engine_api_usage_events (api_key_id, created_at desc, id desc);

create index idx_engine_api_usage_events_owner_created
    on engine_api_usage_events (owner_user_id, created_at desc, id desc);

create index idx_engine_api_usage_events_office_created
    on engine_api_usage_events (office_id, created_at desc, id desc);

create index idx_engine_api_usage_events_capability_created
    on engine_api_usage_events (capability, created_at desc, id desc);

create index idx_engine_api_usage_events_session_created
    on engine_api_usage_events (review_session_id, created_at desc, id desc);

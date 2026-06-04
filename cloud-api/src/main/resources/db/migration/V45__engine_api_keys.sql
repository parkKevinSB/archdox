create table engine_api_keys (
    id bigserial primary key,
    key_id varchar(80) not null unique,
    key_prefix varchar(160) not null,
    secret_hash varchar(128) not null,
    display_name varchar(240) not null,
    owner_user_id bigint not null references users(id),
    office_id bigint references offices(id),
    issued_by_user_id bigint not null references users(id),
    scopes text not null,
    status varchar(40) not null,
    expires_at timestamptz,
    last_used_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_engine_api_keys_owner_created
    on engine_api_keys (owner_user_id, created_at desc, id desc);

create index idx_engine_api_keys_office_created
    on engine_api_keys (office_id, created_at desc, id desc);

create index idx_engine_api_keys_status_updated
    on engine_api_keys (status, updated_at desc, id desc);

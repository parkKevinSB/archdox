alter table archdox_agents
    add column auth_mode text not null default 'SHARED_SECRET',
    add column device_secret_hash text,
    add column paired_at timestamptz,
    add column last_authenticated_at timestamptz;

create table archdox_agent_install_tokens (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    token_hash text not null unique,
    status text not null,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_by bigint not null references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index ix_archdox_agent_install_tokens_office_status
    on archdox_agent_install_tokens (office_id, status, expires_at);

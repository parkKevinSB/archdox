create table archdox_agents (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    agent_code text not null,
    deployment_mode text not null default 'LOCAL_OFFICE',
    version text,
    status text not null,
    capabilities_json jsonb,
    storage_profile_json jsonb,
    last_seen_at timestamptz,
    registered_at timestamptz not null,
    updated_at timestamptz not null,
    unique (office_id, agent_code)
);

create index ix_archdox_agents_office_status on archdox_agents (office_id, status);

create table archdox_agent_heartbeats (
    id bigserial primary key,
    agent_id bigint not null references archdox_agents(id),
    version text,
    disk_free_bytes bigint,
    pending_jobs integer,
    recent_error_count integer,
    received_at timestamptz not null
);

create index ix_archdox_agent_heartbeats_agent_received on archdox_agent_heartbeats (agent_id, received_at desc);

create table archdox_agent_commands (
    id bigserial primary key,
    agent_id bigint not null references archdox_agents(id),
    command_type text not null,
    payload_json jsonb not null,
    status text not null,
    delivered_at timestamptz,
    ack_at timestamptz,
    completed_at timestamptz,
    failed_at timestamptz,
    result_json jsonb,
    error_message text,
    created_at timestamptz not null,
    expires_at timestamptz not null
);

create index ix_archdox_agent_commands_agent_status on archdox_agent_commands (agent_id, status);
create index ix_archdox_agent_commands_status_expires on archdox_agent_commands (status, expires_at);

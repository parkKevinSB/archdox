create table archdox_agent_sessions (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    agent_id bigint not null references archdox_agents(id),
    api_instance_id text not null,
    websocket_session_id text not null,
    status text not null,
    connected_at timestamptz not null,
    last_seen_at timestamptz not null,
    disconnected_at timestamptz,
    disconnect_reason text,
    unique (api_instance_id, websocket_session_id)
);

create index ix_archdox_agent_sessions_agent_status
    on archdox_agent_sessions (agent_id, status, last_seen_at desc);

create index ix_archdox_agent_sessions_instance_status
    on archdox_agent_sessions (api_instance_id, status);

create index ix_archdox_agent_sessions_office_status
    on archdox_agent_sessions (office_id, status, last_seen_at desc);

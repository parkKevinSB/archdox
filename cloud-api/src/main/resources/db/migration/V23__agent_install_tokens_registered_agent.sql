alter table archdox_agent_install_tokens
    add column agent_id bigint references archdox_agents(id);

create index ix_archdox_agent_install_tokens_agent_status
    on archdox_agent_install_tokens (agent_id, status, expires_at);

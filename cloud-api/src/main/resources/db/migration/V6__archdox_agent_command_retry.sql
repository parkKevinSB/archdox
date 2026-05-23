alter table archdox_agent_commands
    add column attempt_count integer not null default 0,
    add column max_attempts integer not null default 5,
    add column last_attempt_at timestamptz,
    add column next_attempt_at timestamptz;

create index ix_archdox_agent_commands_status_next_attempt
    on archdox_agent_commands (status, next_attempt_at);

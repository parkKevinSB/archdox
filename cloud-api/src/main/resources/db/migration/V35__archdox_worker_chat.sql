create table archdox_worker_chat_sessions (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    project_id bigint not null references projects(id) on delete cascade,
    site_id bigint references sites(id) on delete set null,
    report_id bigint references inspection_reports(id) on delete set null,
    user_id bigint not null references users(id),
    status varchar(40) not null,
    stage varchar(60) not null,
    title varchar(240) not null,
    last_message_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index ix_worker_chat_sessions_project_user_status
    on archdox_worker_chat_sessions (office_id, project_id, user_id, status, updated_at desc);

create index ix_worker_chat_sessions_project_updated
    on archdox_worker_chat_sessions (office_id, project_id, updated_at desc);

create index ix_worker_chat_sessions_report
    on archdox_worker_chat_sessions (office_id, report_id);

create table archdox_worker_chat_messages (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    session_id bigint not null references archdox_worker_chat_sessions(id) on delete cascade,
    user_id bigint references users(id),
    role varchar(40) not null,
    status varchar(40) not null,
    content text not null,
    worker_request_id uuid,
    worker_action_type varchar(120),
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index ix_worker_chat_messages_thread_created
    on archdox_worker_chat_messages (office_id, session_id, created_at asc, id asc);

create index ix_worker_chat_messages_worker_request
    on archdox_worker_chat_messages (worker_request_id);

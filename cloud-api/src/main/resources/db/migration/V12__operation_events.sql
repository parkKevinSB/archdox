create table operation_events (
    id bigserial primary key,
    office_id bigint references offices(id),
    severity text not null,
    event_type text not null,
    workflow_type text,
    workflow_key text,
    resource_type text,
    resource_id text,
    actor_user_id bigint references users(id),
    correlation_id text,
    message text not null,
    payload_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null
);

create index ix_operation_events_office_created
    on operation_events (office_id, created_at desc);

create index ix_operation_events_office_resource
    on operation_events (office_id, resource_type, resource_id, created_at desc);

create index ix_operation_events_office_workflow
    on operation_events (office_id, workflow_type, workflow_key, created_at desc);

create index ix_operation_events_event_type
    on operation_events (event_type, created_at desc);

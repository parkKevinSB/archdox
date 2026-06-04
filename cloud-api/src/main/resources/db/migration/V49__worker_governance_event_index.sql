create index if not exists ix_operation_events_worker_governance
    on operation_events (workflow_type, created_at desc, office_id)
    where workflow_type = 'archdox-worker';

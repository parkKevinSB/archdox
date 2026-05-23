alter table document_delivery_requests
    add column prepared_storage_kind text,
    add column prepared_storage_ref text,
    add column prepared_expires_at timestamptz,
    add column download_ready_at timestamptz,
    add column agent_command_id bigint references archdox_agent_commands(id);

create index ix_document_delivery_requests_agent_command
    on document_delivery_requests (agent_command_id);

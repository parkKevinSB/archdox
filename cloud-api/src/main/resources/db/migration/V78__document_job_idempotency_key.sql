alter table document_jobs
    add column if not exists idempotency_key text;

create unique index if not exists ux_document_jobs_office_idempotency_key
    on document_jobs (office_id, idempotency_key)
    where idempotency_key is not null;

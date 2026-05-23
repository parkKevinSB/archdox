alter table document_jobs
    add column progress_step text not null default 'QUEUED',
    add column progress_percent integer not null default 0,
    add column progress_message text;

create index ix_document_jobs_office_progress on document_jobs (office_id, progress_step, updated_at desc);

alter table inspection_reports
    add column content_revision integer not null default 1,
    add column submitted_revision integer,
    add column generated_revision integer,
    add column last_document_job_id bigint references document_jobs(id);

alter table document_jobs
    add column report_revision integer not null default 1;

create index ix_document_jobs_report_revision on document_jobs (report_id, report_revision);

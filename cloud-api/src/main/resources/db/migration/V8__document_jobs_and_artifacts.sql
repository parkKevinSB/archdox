create table document_jobs (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    report_id bigint not null references inspection_reports(id),
    project_id bigint references projects(id),
    template_id bigint,
    status text not null,
    requested_by bigint references users(id),
    worker_type text not null,
    output_format text not null,
    input_snapshot_json jsonb not null,
    error_code text,
    error_message text,
    requested_at timestamptz not null,
    started_at timestamptz,
    completed_at timestamptz,
    updated_at timestamptz not null
);

create index ix_document_jobs_office_status_updated on document_jobs (office_id, status, updated_at desc);
create index ix_document_jobs_report_requested on document_jobs (report_id, requested_at desc);

create table document_artifacts (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    document_job_id bigint not null references document_jobs(id),
    report_id bigint not null references inspection_reports(id),
    artifact_type text not null,
    storage_kind text not null,
    storage_ref text not null,
    file_name text not null,
    mime_type text not null,
    bytes bigint not null,
    hash_sha256 text not null,
    created_at timestamptz not null,
    unique (document_job_id, artifact_type, file_name)
);

create index ix_document_artifacts_job on document_artifacts (document_job_id);
create index ix_document_artifacts_report on document_artifacts (report_id);
create index ix_document_artifacts_storage_ref on document_artifacts (storage_ref);

create table document_delivery_requests (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    document_job_id bigint not null references document_jobs(id),
    artifact_id bigint references document_artifacts(id),
    channel text not null,
    status text not null,
    recipient_ref text,
    requested_by bigint references users(id),
    error_message text,
    requested_at timestamptz not null,
    completed_at timestamptz,
    updated_at timestamptz not null
);

create index ix_document_delivery_requests_office_status on document_delivery_requests (office_id, status, updated_at desc);
create index ix_document_delivery_requests_job on document_delivery_requests (document_job_id);

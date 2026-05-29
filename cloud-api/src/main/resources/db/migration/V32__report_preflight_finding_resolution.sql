alter table report_preflight_review_findings
    add column resolution_status varchar(40) not null default 'OPEN',
    add column resolution_note text,
    add column resolved_by bigint references users(id),
    add column resolved_at timestamptz;

create index idx_report_preflight_review_findings_resolution
    on report_preflight_review_findings (office_id, review_run_id, resolution_status);

create table photos (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    project_id bigint references projects(id),
    report_id bigint references inspection_reports(id),
    step_code text,
    checklist_item_id bigint,
    capture_kind text not null,
    status text not null,
    mime_type text not null,
    width integer,
    height integer,
    bytes bigint,
    hash_sha256 text not null,
    storage_kind text not null,
    storage_ref text not null,
    thumbnail_storage_ref text,
    upload_target text not null,
    requested_by bigint references users(id),
    confirmed_by bigint references users(id),
    taken_at timestamptz,
    gps_lat numeric(9, 6),
    gps_lng numeric(9, 6),
    created_at timestamptz not null,
    confirmed_at timestamptz,
    updated_at timestamptz not null
);

create index ix_photos_office_report on photos (office_id, report_id);
create index ix_photos_office_project on photos (office_id, project_id);
create index ix_photos_office_hash on photos (office_id, hash_sha256);
create index ix_photos_office_status_created on photos (office_id, status, created_at desc);

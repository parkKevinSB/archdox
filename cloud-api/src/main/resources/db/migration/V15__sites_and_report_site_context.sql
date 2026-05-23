create table sites (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    project_id bigint not null references projects(id),
    site_code text,
    name text not null,
    address text,
    site_type text,
    start_date date,
    end_date date,
    metadata_json jsonb,
    status text not null,
    created_by bigint references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index ix_sites_office_project_status on sites (office_id, project_id, status);
create index ix_sites_project_updated on sites (project_id, updated_at desc);

alter table inspection_reports
    add column site_id bigint references sites(id);

create index ix_inspection_reports_site on inspection_reports (site_id);

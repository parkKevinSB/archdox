create table site_supervision_entries (
    id bigserial primary key,
    office_id bigint not null,
    project_id bigint not null,
    site_id bigint not null,
    entry_date date,
    floor_area text,
    trade_code text,
    trade_name text,
    process_code text,
    process_name text,
    item_code text,
    item_name text,
    supervision_content text,
    result_status text,
    issue_text text,
    action_result text,
    photo_ids jsonb not null default '[]'::jsonb,
    status text not null,
    source_type text not null,
    source_report_id bigint not null,
    source_report_revision integer not null,
    source_step_code text not null,
    source_step_client_revision integer not null,
    source_group_key text,
    source_item_key text,
    source_entry_key text not null,
    catalog_code text,
    catalog_version integer,
    created_by bigint,
    updated_by bigint,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index ix_site_supervision_entries_site_date
    on site_supervision_entries (office_id, project_id, site_id, entry_date desc, id desc);

create index ix_site_supervision_entries_trade
    on site_supervision_entries (office_id, site_id, trade_code, process_code, item_code);

create index ix_site_supervision_entries_source_report
    on site_supervision_entries (office_id, source_report_id, source_report_revision, source_step_code);

create unique index ux_site_supervision_entries_source_entry
    on site_supervision_entries (
        office_id,
        source_report_id,
        source_report_revision,
        source_step_code,
        source_entry_key
    );

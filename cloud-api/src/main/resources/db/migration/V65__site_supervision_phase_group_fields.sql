alter table site_supervision_entries
    add column if not exists group_type text,
    add column if not exists phase_code text,
    add column if not exists phase_name text;

update site_supervision_entries
set group_type = 'TRADE'
where group_type is null;

create index if not exists ix_site_supervision_entries_phase
    on site_supervision_entries (office_id, site_id, phase_code, process_code, inspection_item_code);

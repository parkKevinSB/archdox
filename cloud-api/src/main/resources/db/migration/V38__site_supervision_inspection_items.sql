alter table site_supervision_entries
    add column inspection_item_code text,
    add column inspection_item_name text;

update site_supervision_entries
set inspection_item_code = item_code,
    inspection_item_name = item_name
where inspection_item_code is null
  and inspection_item_name is null;

create index ix_site_supervision_entries_inspection_item
    on site_supervision_entries (office_id, site_id, trade_code, process_code, inspection_item_code);

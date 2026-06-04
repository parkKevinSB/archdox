alter table photos
    add column site_id bigint references sites(id),
    add column site_supervision_entry_id bigint,
    add column trade_code text,
    add column process_code text,
    add column inspection_item_code text,
    add column caption text,
    add column location_note text,
    add column drawing_ref text;

update photos photo
set site_id = report.site_id
from inspection_reports report
where photo.report_id = report.id
  and photo.site_id is null;

create index ix_photos_office_site on photos (office_id, site_id);
create index ix_photos_supervision_context
    on photos (office_id, site_id, trade_code, process_code, inspection_item_code);
create index ix_photos_site_supervision_entry
    on photos (office_id, site_supervision_entry_id);

alter table sites
    add column supervision_work_mode text not null default 'NON_RESIDENT';

create index ix_sites_supervision_work_mode on sites (supervision_work_mode);

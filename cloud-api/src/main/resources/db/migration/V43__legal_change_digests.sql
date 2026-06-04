create table legal_change_digests (
    id bigserial primary key,
    change_set_id bigint not null unique references legal_change_sets(id),
    status varchar(40) not null,
    source varchar(40) not null,
    title varchar(300) not null,
    summary text not null,
    impact_summary text,
    affected_report_types_json jsonb not null default '[]'::jsonb,
    affected_catalog_items_json jsonb not null default '[]'::jsonb,
    ai_harness_run_id varchar(160),
    effective_date date,
    detected_at timestamptz not null,
    published_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_legal_change_digests_published
    on legal_change_digests (status, published_at desc, id desc);

create index idx_legal_change_digests_detected
    on legal_change_digests (detected_at desc, id desc);

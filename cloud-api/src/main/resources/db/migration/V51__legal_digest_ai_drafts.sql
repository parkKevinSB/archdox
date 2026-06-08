create table legal_digest_ai_drafts (
    id bigserial primary key,
    digest_id bigint not null references legal_change_digests(id),
    change_set_id bigint not null references legal_change_sets(id),
    status varchar(40) not null,
    worker_request_id uuid not null,
    worker_status varchar(60) not null,
    result_code varchar(160),
    ai_harness_run_id varchar(160),
    digest_draft_status varchar(80),
    title varchar(300) not null,
    summary text not null,
    impact_summary text,
    confidence varchar(80),
    affected_report_types_json jsonb not null default '[]'::jsonb,
    affected_catalog_items_json jsonb not null default '[]'::jsonb,
    key_articles_json jsonb not null default '[]'::jsonb,
    review_notes text,
    publication_applied boolean not null default false,
    corpus_mutated boolean not null default false,
    digest_mutated boolean not null default false,
    generated_by_user_id bigint not null,
    applied_by_user_id bigint,
    generated_at timestamptz not null,
    applied_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index uk_legal_digest_ai_drafts_worker_request
    on legal_digest_ai_drafts (worker_request_id);

create index idx_legal_digest_ai_drafts_digest
    on legal_digest_ai_drafts (digest_id, generated_at desc, id desc);

create index idx_legal_digest_ai_drafts_status
    on legal_digest_ai_drafts (status, generated_at desc, id desc);

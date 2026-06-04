create table legal_sources (
    id bigserial primary key,
    code varchar(120) not null unique,
    source_type varchar(80) not null,
    display_name varchar(240) not null,
    base_url varchar(500),
    status varchar(40) not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table legal_acts (
    id bigserial primary key,
    source_id bigint not null references legal_sources(id),
    act_code varchar(120) not null,
    act_name varchar(240) not null,
    act_type varchar(80) not null,
    jurisdiction varchar(80) not null,
    source_law_id varchar(160),
    status varchar(40) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (source_id, act_code)
);

create index idx_legal_acts_source_status
    on legal_acts (source_id, status, act_code);

create table legal_versions (
    id bigserial primary key,
    act_id bigint not null references legal_acts(id),
    source_version_key varchar(160) not null,
    promulgation_date date,
    effective_date date,
    source_url varchar(800),
    content_hash varchar(100) not null,
    source_metadata_json jsonb not null default '{}'::jsonb,
    captured_at timestamptz not null,
    unique (act_id, source_version_key)
);

create index idx_legal_versions_act_captured
    on legal_versions (act_id, captured_at desc, id desc);

create table legal_articles (
    id bigserial primary key,
    act_id bigint not null references legal_acts(id),
    article_key varchar(160) not null,
    article_no varchar(80) not null,
    article_title varchar(300),
    parent_article_key varchar(160),
    sort_order integer not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (act_id, article_key)
);

create index idx_legal_articles_act_sort
    on legal_articles (act_id, sort_order, article_key);

create table legal_article_versions (
    id bigserial primary key,
    article_id bigint not null references legal_articles(id),
    legal_version_id bigint not null references legal_versions(id),
    article_key varchar(160) not null,
    article_no varchar(80) not null,
    article_title varchar(300),
    article_text text not null,
    normalized_text text not null,
    content_hash varchar(100) not null,
    effective_date date,
    source_metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    unique (article_id, legal_version_id)
);

create index idx_legal_article_versions_version
    on legal_article_versions (legal_version_id, article_key);

create index idx_legal_article_versions_hash
    on legal_article_versions (content_hash);

create table legal_sync_runs (
    id bigserial primary key,
    trigger_type varchar(80) not null,
    source_code varchar(120) not null,
    status varchar(40) not null,
    started_by_user_id bigint references users(id),
    started_at timestamptz not null,
    completed_at timestamptz,
    failure_code varchar(160),
    summary_json jsonb not null default '{}'::jsonb
);

create index idx_legal_sync_runs_started
    on legal_sync_runs (started_at desc, id desc);

create index idx_legal_sync_runs_source_status
    on legal_sync_runs (source_code, status, started_at desc);

create table legal_change_sets (
    id bigserial primary key,
    act_id bigint not null references legal_acts(id),
    sync_run_id bigint references legal_sync_runs(id),
    previous_version_id bigint references legal_versions(id),
    new_version_id bigint not null references legal_versions(id),
    status varchar(40) not null,
    effective_date date,
    detected_at timestamptz not null,
    summary text not null,
    metadata_json jsonb not null default '{}'::jsonb
);

create index idx_legal_change_sets_act_detected
    on legal_change_sets (act_id, detected_at desc, id desc);

create index idx_legal_change_sets_run
    on legal_change_sets (sync_run_id, id);

create table legal_article_diffs (
    id bigserial primary key,
    change_set_id bigint not null references legal_change_sets(id),
    article_id bigint references legal_articles(id),
    article_key varchar(160) not null,
    article_no varchar(80),
    change_type varchar(40) not null,
    before_article_version_id bigint references legal_article_versions(id),
    after_article_version_id bigint references legal_article_versions(id),
    before_hash varchar(100),
    after_hash varchar(100),
    diff_summary text not null,
    created_at timestamptz not null
);

create index idx_legal_article_diffs_change_set
    on legal_article_diffs (change_set_id, id);

create index idx_legal_article_diffs_article
    on legal_article_diffs (article_id, created_at desc);

create table legal_domain_bindings (
    id bigserial primary key,
    binding_scope varchar(80) not null,
    binding_key varchar(160) not null,
    act_id bigint not null references legal_acts(id),
    article_id bigint references legal_articles(id),
    report_type varchar(120),
    catalog_code varchar(160),
    catalog_version integer,
    checklist_item_code varchar(180),
    relevance varchar(40) not null,
    status varchar(40) not null,
    effective_from date,
    effective_to date,
    notes text,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_legal_domain_bindings_scope
    on legal_domain_bindings (binding_scope, binding_key, status);

create index idx_legal_domain_bindings_report
    on legal_domain_bindings (report_type, status);

create table legal_review_runs (
    id bigserial primary key,
    office_id bigint references offices(id),
    report_id bigint references inspection_reports(id),
    review_type varchar(80) not null,
    status varchar(40) not null,
    legal_context_json jsonb not null default '{}'::jsonb,
    ai_harness_run_id varchar(160),
    started_at timestamptz not null,
    completed_at timestamptz,
    failure_code varchar(160)
);

create index idx_legal_review_runs_report_started
    on legal_review_runs (report_id, started_at desc, id desc);

create table legal_review_findings (
    id bigserial primary key,
    review_run_id bigint not null references legal_review_runs(id),
    office_id bigint references offices(id),
    report_id bigint references inspection_reports(id),
    source varchar(40) not null,
    severity varchar(40) not null,
    code varchar(120) not null,
    category varchar(80) not null,
    message text not null,
    act_id bigint references legal_acts(id),
    article_id bigint references legal_articles(id),
    legal_version_id bigint references legal_versions(id),
    effective_date date,
    evidence_json jsonb not null default '{}'::jsonb,
    recommendation text,
    created_at timestamptz not null
);

create index idx_legal_review_findings_run
    on legal_review_findings (review_run_id, id);

create index idx_legal_review_findings_report
    on legal_review_findings (report_id, created_at desc);

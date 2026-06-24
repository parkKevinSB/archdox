create table platform_ops_control_profiles (
    id bigserial primary key,
    signal_kind varchar(40) not null,
    scope_type varchar(40) not null,
    model_id varchar(160),
    signal_key varchar(96) not null,
    signal_text text not null,
    severity varchar(40) not null,
    i_weight numeric(8, 3) not null,
    hit_count integer not null,
    source_daily_report_id bigint references platform_ops_daily_reports(id),
    notes text,
    status varchar(40) not null,
    created_by_user_id bigint references users(id),
    updated_by_user_id bigint references users(id),
    first_observed_at timestamptz not null,
    last_observed_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_platform_ops_control_profiles_scope
    on platform_ops_control_profiles (signal_kind, scope_type, model_id, status);

create index idx_platform_ops_control_profiles_observed
    on platform_ops_control_profiles (last_observed_at desc, id desc);

create unique index ux_platform_ops_control_profiles_signal
    on platform_ops_control_profiles (signal_kind, scope_type, coalesce(model_id, ''), signal_key);

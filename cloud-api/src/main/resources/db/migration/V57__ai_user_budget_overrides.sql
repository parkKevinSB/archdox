create table ai_user_budget_overrides (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    user_id bigint not null references users(id),
    daily_call_limit integer,
    monthly_token_limit bigint,
    monthly_budget_amount numeric(18, 8),
    budget_currency varchar(12) not null default 'USD',
    reason text not null,
    expires_at timestamptz,
    created_by_user_id bigint references users(id),
    disabled_by_user_id bigint references users(id),
    disable_reason text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    disabled_at timestamptz
);

create index idx_ai_user_budget_overrides_office_user
    on ai_user_budget_overrides (office_id, user_id, created_at desc);

create index idx_ai_user_budget_overrides_active
    on ai_user_budget_overrides (office_id, user_id, expires_at)
    where disabled_at is null;

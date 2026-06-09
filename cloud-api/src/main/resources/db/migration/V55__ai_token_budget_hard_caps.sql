alter table ai_harness_policies
    add column max_output_tokens integer not null default 1200,
    add column budget_enforcement_enabled boolean not null default true,
    add column monthly_budget_amount numeric(18, 8),
    add column budget_currency varchar(12) not null default 'USD',
    add column daily_call_limit integer not null default 30,
    add column monthly_token_limit bigint not null default 500000;

update ai_harness_policies
set max_output_tokens = case
        when policy_key = 'LEGAL_DIGEST_ENRICHMENT' then 1200
        when policy_key = 'PLATFORM_OPS_DIAGNOSIS' then 1000
        else 1200
    end,
    budget_enforcement_enabled = true,
    daily_call_limit = coalesce(daily_call_limit, 30),
    monthly_token_limit = coalesce(monthly_token_limit, 500000),
    budget_currency = coalesce(nullif(budget_currency, ''), 'USD');

alter table office_ai_policies
    add column max_output_tokens integer not null default 2000,
    add column per_user_daily_call_limit integer not null default 30,
    add column per_user_monthly_token_limit bigint not null default 500000;

update office_ai_policies
set budget_enforcement_enabled = true,
    daily_call_limit = coalesce(daily_call_limit, 100),
    monthly_token_limit = coalesce(monthly_token_limit, 2000000),
    max_output_tokens = coalesce(max_output_tokens, 2000),
    per_user_daily_call_limit = coalesce(per_user_daily_call_limit, 30),
    per_user_monthly_token_limit = coalesce(per_user_monthly_token_limit, 500000),
    budget_currency = coalesce(nullif(budget_currency, ''), 'USD');

alter table ai_model_call_logs
    add column user_id bigint references users(id);

create index idx_ai_model_call_logs_user_completed_at
    on ai_model_call_logs (user_id, completed_at desc);

create index idx_ai_model_call_logs_feature_completed_at
    on ai_model_call_logs (feature, completed_at desc);

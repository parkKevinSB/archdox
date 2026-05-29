alter table office_ai_policies
    add column budget_enforcement_enabled boolean not null default false,
    add column monthly_budget_amount numeric(18, 8),
    add column budget_currency varchar(12) not null default 'USD',
    add column daily_call_limit integer,
    add column monthly_token_limit bigint;

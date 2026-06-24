create table platform_ops_daily_reports (
    id bigserial primary key,
    run_id bigint not null references platform_ops_runs(id),
    due_at timestamptz not null,
    period_from timestamptz not null,
    period_to timestamptz not null,
    status varchar(40) not null,
    severity varchar(40) not null,
    title varchar(240) not null,
    summary text not null,
    report_path text,
    ai_harness_run_id varchar(120),
    p_like_json jsonb not null default '[]'::jsonb,
    i_like_json jsonb not null default '[]'::jsonb,
    d_like_json jsonb not null default '[]'::jsonb,
    recommendations_json jsonb not null default '[]'::jsonb,
    evidence_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null
);

create index idx_platform_ops_daily_reports_due
    on platform_ops_daily_reports (due_at desc, id desc);

create index idx_platform_ops_daily_reports_run
    on platform_ops_daily_reports (run_id);

with selected_provider as (
    select id, default_model
    from ai_provider_credentials
    where provider_code in ('openai-main', 'fake-ops')
    order by case provider_code when 'openai-main' then 0 else 1 end
    limit 1
)
insert into ai_harness_policies (
    policy_key,
    display_name,
    description,
    enabled,
    provider_credential_id,
    model_name,
    max_attempts,
    timeout_seconds,
    max_output_tokens,
    budget_enforcement_enabled,
    daily_call_limit,
    monthly_token_limit,
    created_at,
    updated_at
)
select
    'PLATFORM_OPS_DAILY_REPORT',
    '일일 운영 리포트 AI',
    'ArchDox 런타임, Worker, MCP, AI, 운영 이벤트 근거를 바탕으로 일일 운영 리포트 초안을 생성합니다.',
    true,
    selected_provider.id,
    coalesce(selected_provider.default_model, 'gpt-4.1-mini'),
    2,
    90,
    1400,
    true,
    10,
    300000,
    now(),
    now()
from (select 1) seed
left join selected_provider on true
on conflict (policy_key) do nothing;

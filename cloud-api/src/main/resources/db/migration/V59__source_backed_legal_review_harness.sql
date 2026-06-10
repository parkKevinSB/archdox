with selected_provider as (
    select id, default_model
    from ai_provider_credentials
    where provider_code in ('openai-main', 'fake-review')
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
    budget_currency,
    created_at,
    updated_at
)
select
    'SOURCE_BACKED_LEGAL_REVIEW',
    '법령 근거 기반 검토 AI',
    '감리 리포트 입력을 ArchDox 법령 corpus와 업무-법령 매핑 근거 안에서 dry-run 검토하고, 통과 이유와 확인 필요 항목을 생성합니다.',
    true,
    selected_provider.id,
    coalesce(selected_provider.default_model, 'gpt-4.1-mini'),
    2,
    90,
    1200,
    true,
    40,
    300000,
    'USD',
    now(),
    now()
from (select 1) seed
left join selected_provider on true
where not exists (
    select 1
    from ai_harness_policies
    where policy_key = 'SOURCE_BACKED_LEGAL_REVIEW'
);

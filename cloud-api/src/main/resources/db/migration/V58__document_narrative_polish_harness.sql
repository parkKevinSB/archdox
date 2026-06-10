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
    'DOCUMENT_NARRATIVE_POLISH',
    '문서 문장 다듬기 AI',
    '문서 생성 직전 감리일지 문장을 보고서 문체로 다듬는 초안을 생성합니다. 원본 업무 데이터는 수정하지 않습니다.',
    true,
    selected_provider.id,
    coalesce(selected_provider.default_model, 'gpt-4.1-mini'),
    2,
    90,
    900,
    true,
    50,
    300000,
    'USD',
    now(),
    now()
from (select 1) seed
left join selected_provider on true
where not exists (
    select 1
    from ai_harness_policies
    where policy_key = 'DOCUMENT_NARRATIVE_POLISH'
);

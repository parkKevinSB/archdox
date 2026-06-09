insert into office_ai_policies (
    office_id,
    ai_enabled,
    document_review_ai_enabled,
    document_generation_ai_enabled,
    preferred_provider_credential_id,
    credential_delivery_mode,
    budget_enforcement_enabled,
    monthly_budget_amount,
    budget_currency,
    daily_call_limit,
    monthly_token_limit,
    max_output_tokens,
    per_user_daily_call_limit,
    per_user_monthly_token_limit,
    policy_version,
    created_at,
    updated_at
)
select
    offices.id,
    false,
    false,
    false,
    null,
    'PROXY_ONLY',
    true,
    null,
    'USD',
    100,
    2000000,
    2000,
    30,
    500000,
    1,
    now(),
    now()
from offices
where not exists (
    select 1
    from office_ai_policies policy
    where policy.office_id = offices.id
);

insert into ai_model_pricing_rules (
    provider_code,
    model_name,
    currency,
    input_token_price_per_million,
    output_token_price_per_million,
    status,
    created_at,
    updated_at
)
select
    seed.provider_code,
    seed.model_name,
    seed.currency,
    seed.input_price,
    seed.output_price,
    'ACTIVE',
    now(),
    now()
from (
    values
        ('openai-main', 'gpt-4.1-mini', 'USD', 0.40000000::numeric, 1.60000000::numeric),
        ('fake-review', 'fake-review-model', 'USD', 0.00000000::numeric, 0.00000000::numeric),
        ('fake-ops', 'fake-ops-model', 'USD', 0.00000000::numeric, 0.00000000::numeric)
) as seed(provider_code, model_name, currency, input_price, output_price)
where not exists (
    select 1
    from ai_model_pricing_rules rule
    where rule.provider_code = seed.provider_code
      and rule.model_name = seed.model_name
      and rule.status = 'ACTIVE'
);

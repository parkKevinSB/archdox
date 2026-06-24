with openai_main as (
    select id, default_model
    from ai_provider_credentials
    where provider_code = 'openai-main'
    order by id
    limit 1
)
update ai_harness_policies policy
set provider_credential_id = openai_main.id,
    model_name = coalesce(openai_main.default_model, 'gpt-4.1-mini'),
    display_name = case policy.policy_key
        when 'PLATFORM_OPS_DIAGNOSIS' then '운영 이슈 원인 분석 AI'
        when 'PLATFORM_OPS_DAILY_REPORT' then '일일 운영 리포트 AI'
        else policy.display_name
    end,
    description = case policy.policy_key
        when 'PLATFORM_OPS_DIAGNOSIS' then '특정 ArchDox 운영 이슈의 원인 후보와 확인할 조치 초안을 생성합니다.'
        when 'PLATFORM_OPS_DAILY_REPORT' then 'ArchDox 런타임, Worker, MCP, AI, 운영 이벤트 근거를 바탕으로 일일 운영 리포트 초안을 생성합니다.'
        else policy.description
    end,
    enabled = true,
    updated_at = now()
from openai_main
where policy.policy_key in ('PLATFORM_OPS_DIAGNOSIS', 'PLATFORM_OPS_DAILY_REPORT');

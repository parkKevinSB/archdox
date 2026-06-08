create table ai_harness_policies (
    id bigserial primary key,
    policy_key varchar(120) not null unique,
    display_name text not null,
    description text,
    enabled boolean not null default false,
    provider_credential_id bigint references ai_provider_credentials(id),
    model_name varchar(160),
    max_attempts integer not null default 2,
    timeout_seconds bigint not null default 90,
    policy_version bigint not null default 1,
    updated_by_user_id bigint references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_ai_harness_policies_provider
    on ai_harness_policies (provider_credential_id);

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
    created_at,
    updated_at
)
select
    'LEGAL_DIGEST_ENRICHMENT',
    '법령 변경 게시글 AI 초안',
    '동기화된 법령 변경사항을 근거로 사용자용 게시글 초안, 요약, 업무 영향 메모를 생성합니다. 법령 corpus나 게시 상태는 직접 수정하지 않습니다.',
    true,
    selected_provider.id,
    coalesce(selected_provider.default_model, 'gpt-4.1-mini'),
    2,
    90,
    now(),
    now()
from (select 1) seed
left join selected_provider on true;

with selected_provider as (
    select id, default_model
    from ai_provider_credentials
    where provider_code in ('fake-ops', 'openai-main')
    order by case provider_code when 'fake-ops' then 0 else 1 end
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
    created_at,
    updated_at
)
select
    'PLATFORM_OPS_DIAGNOSIS',
    '플랫폼 운영 진단 AI',
    '운영 이벤트, 작업 상태, 장애 징후 스냅샷을 기반으로 플랫폼 운영 진단 초안을 생성합니다.',
    true,
    selected_provider.id,
    coalesce(selected_provider.default_model, 'fake-ops-model'),
    2,
    90,
    now(),
    now()
from (select 1) seed
left join selected_provider on true;

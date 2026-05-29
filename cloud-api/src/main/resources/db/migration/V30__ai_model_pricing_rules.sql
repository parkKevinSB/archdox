create table ai_model_pricing_rules (
    id bigserial primary key,
    provider_code varchar(120) not null,
    model_name varchar(160) not null,
    currency varchar(12) not null,
    input_token_price_per_million numeric(18, 8) not null,
    output_token_price_per_million numeric(18, 8) not null,
    status varchar(40) not null,
    created_by_user_id bigint references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    disabled_at timestamptz
);

create index idx_ai_model_pricing_rules_provider_model
    on ai_model_pricing_rules (provider_code, model_name, status, created_at desc);

create index idx_ai_model_pricing_rules_status
    on ai_model_pricing_rules (status, created_at desc);

alter table ai_model_call_logs
    add column pricing_rule_id bigint references ai_model_pricing_rules(id),
    add column cost_currency varchar(12),
    add column estimated_input_cost numeric(18, 8),
    add column estimated_output_cost numeric(18, 8),
    add column estimated_total_cost numeric(18, 8);

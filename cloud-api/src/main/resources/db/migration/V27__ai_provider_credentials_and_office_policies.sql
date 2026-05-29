create table ai_provider_credentials (
    id bigserial primary key,
    provider_code text not null unique,
    display_name text not null,
    provider_type text not null,
    status text not null,
    base_url text,
    default_model text,
    encrypted_api_key text,
    api_key_fingerprint text,
    credential_version bigint not null default 1,
    created_by_user_id bigint references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    published_at timestamptz
);

create index ix_ai_provider_credentials_status on ai_provider_credentials (status);

create table office_ai_policies (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    ai_enabled boolean not null default false,
    document_review_ai_enabled boolean not null default false,
    document_generation_ai_enabled boolean not null default false,
    preferred_provider_credential_id bigint references ai_provider_credentials(id),
    credential_delivery_mode text not null default 'PROXY_ONLY',
    policy_version bigint not null default 1,
    updated_by_user_id bigint references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (office_id)
);

create index ix_office_ai_policies_provider on office_ai_policies (preferred_provider_credential_id);

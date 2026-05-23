create table users (
    id bigserial primary key,
    email text not null,
    password_hash text not null,
    name text not null,
    status text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index ux_users_email_lower on users (lower(email));

create table offices (
    id bigserial primary key,
    office_code text not null unique,
    display_name text not null,
    type text not null,
    plan_code text not null,
    status text not null,
    region text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table office_memberships (
    id bigserial primary key,
    user_id bigint not null references users(id),
    office_id bigint not null references offices(id),
    role text not null,
    status text not null,
    joined_at timestamptz not null,
    unique (user_id, office_id)
);

create index ix_office_memberships_office on office_memberships (office_id);

create table auth_refresh_tokens (
    id bigserial primary key,
    user_id bigint not null references users(id),
    token_hash text not null unique,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null
);

create index ix_auth_refresh_tokens_user on auth_refresh_tokens (user_id);

create table audit_logs (
    id bigserial primary key,
    office_id bigint references offices(id),
    actor_user_id bigint references users(id),
    action text not null,
    target_type text not null,
    target_id text,
    ip text,
    user_agent text,
    metadata_json jsonb,
    created_at timestamptz not null
);

create index ix_audit_logs_office_created on audit_logs (office_id, created_at desc);

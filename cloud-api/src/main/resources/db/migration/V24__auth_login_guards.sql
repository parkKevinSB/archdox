create table auth_login_guards (
    id bigserial primary key,
    scope text not null,
    guard_key_hash text not null,
    display_key text,
    failure_count integer not null default 0,
    first_failed_at timestamptz,
    last_failed_at timestamptz,
    locked_until timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_auth_login_guards_scope_key unique (scope, guard_key_hash)
);

create index ix_auth_login_guards_scope_locked_until
    on auth_login_guards (scope, locked_until);


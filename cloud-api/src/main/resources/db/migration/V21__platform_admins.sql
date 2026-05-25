create table platform_admins (
    id bigserial primary key,
    user_id bigint not null references users(id),
    role text not null,
    status text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (user_id)
);

create index ix_platform_admins_status_role on platform_admins (status, role);

create table office_invitations (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    email text not null,
    role text not null,
    status text not null,
    token_hash text not null unique,
    token_preview text not null,
    invited_by bigint not null references users(id),
    accepted_by bigint references users(id),
    created_at timestamptz not null,
    expires_at timestamptz not null,
    accepted_at timestamptz,
    cancelled_at timestamptz,
    updated_at timestamptz not null
);

create index ix_office_invitations_office_created
    on office_invitations (office_id, created_at desc);

create index ix_office_invitations_office_status
    on office_invitations (office_id, status, updated_at desc);

create unique index ux_office_invitations_pending_email
    on office_invitations (office_id, lower(email))
    where status = 'PENDING';

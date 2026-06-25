create table office_storage_profiles (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    profile_code varchar(80) not null,
    display_name varchar(160) not null,
    provider_type varchar(40) not null,
    status varchar(40) not null,
    endpoint varchar(512),
    region varchar(80) not null,
    bucket_name varchar(255) not null,
    object_prefix varchar(512),
    path_style_access boolean not null default false,
    encrypted_access_key text,
    encrypted_secret_key text,
    access_key_fingerprint varchar(64),
    credential_version bigint not null default 1,
    last_tested_at timestamp with time zone,
    last_test_status varchar(40),
    last_test_message text,
    created_by_user_id bigint,
    updated_by_user_id bigint,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (office_id, profile_code)
);

create index idx_office_storage_profiles_office_status
    on office_storage_profiles (office_id, status);

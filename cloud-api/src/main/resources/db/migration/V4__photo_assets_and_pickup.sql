alter table photos
    add column original_pickup_status text not null default 'PENDING',
    add column original_picked_up_at timestamptz,
    add column original_temporary_deleted_at timestamptz,
    add column pickup_error_message text;

create table photo_assets (
    id bigserial primary key,
    photo_id bigint not null references photos(id),
    asset_type text not null,
    status text not null,
    storage_kind text not null,
    storage_ref text not null,
    mime_type text not null,
    bytes bigint,
    width integer,
    height integer,
    hash_sha256 text,
    temporary boolean not null default false,
    created_at timestamptz not null,
    uploaded_at timestamptz,
    picked_up_at timestamptz,
    deleted_at timestamptz,
    unique (photo_id, asset_type)
);

create index ix_photo_assets_photo on photo_assets (photo_id);
create index ix_photo_assets_storage_ref on photo_assets (storage_ref);

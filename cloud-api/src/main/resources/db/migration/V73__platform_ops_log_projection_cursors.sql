create table platform_ops_log_projection_cursors (
    source_code varchar(80) primary key,
    log_path text not null,
    position_bytes bigint not null,
    file_size_bytes bigint not null,
    last_scanned_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

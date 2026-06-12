CREATE TABLE server_runtime_health_settings (
    singleton_key VARCHAR(64) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    check_interval_ms BIGINT NOT NULL DEFAULT 300000,
    cpu_warn_percent NUMERIC(5, 2) NOT NULL DEFAULT 85.00,
    system_memory_warn_percent NUMERIC(5, 2) NOT NULL DEFAULT 90.00,
    jvm_heap_warn_percent NUMERIC(5, 2) NOT NULL DEFAULT 90.00,
    event_cooldown_ms BIGINT NOT NULL DEFAULT 900000,
    updated_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE server_runtime_health_settings
    ALTER COLUMN cpu_warn_percent TYPE DOUBLE PRECISION USING cpu_warn_percent::DOUBLE PRECISION,
    ALTER COLUMN system_memory_warn_percent TYPE DOUBLE PRECISION USING system_memory_warn_percent::DOUBLE PRECISION,
    ALTER COLUMN jvm_heap_warn_percent TYPE DOUBLE PRECISION USING jvm_heap_warn_percent::DOUBLE PRECISION;

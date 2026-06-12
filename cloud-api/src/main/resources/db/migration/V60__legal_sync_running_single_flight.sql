create unique index ux_legal_sync_runs_running_source
    on legal_sync_runs (source_code)
    where status = 'RUNNING';

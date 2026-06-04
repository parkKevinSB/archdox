alter table engine_api_keys
    add column daily_request_unit_limit integer not null default 1000;

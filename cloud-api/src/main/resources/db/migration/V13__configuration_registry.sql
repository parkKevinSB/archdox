create table document_templates (
    id bigserial primary key,
    office_id bigint references offices(id),
    template_code text not null,
    name text not null,
    report_type text,
    status text not null,
    created_by bigint references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index ux_document_templates_scope_report_code
    on document_templates (coalesce(office_id, -1), coalesce(report_type, ''), template_code);

create index ix_document_templates_office_report
    on document_templates (office_id, report_type, updated_at desc);

create table document_template_revisions (
    id bigserial primary key,
    template_id bigint not null references document_templates(id),
    version integer not null,
    status text not null,
    template_storage_kind text,
    template_storage_ref text,
    schema_json jsonb not null default '{}'::jsonb,
    compose_policy_json jsonb not null default '{}'::jsonb,
    ai_prompts_json jsonb not null default '{}'::jsonb,
    created_by bigint references users(id),
    published_by bigint references users(id),
    created_at timestamptz not null,
    published_at timestamptz,
    unique (template_id, version)
);

create index ix_document_template_revisions_template
    on document_template_revisions (template_id, version desc);

create index ix_document_template_revisions_status
    on document_template_revisions (status, published_at desc);

create table workflow_definitions (
    id bigserial primary key,
    office_id bigint references offices(id),
    workflow_code text not null,
    name text not null,
    report_type text,
    status text not null,
    created_by bigint references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index ux_workflow_definitions_scope_report_code
    on workflow_definitions (coalesce(office_id, -1), coalesce(report_type, ''), workflow_code);

create index ix_workflow_definitions_office_report
    on workflow_definitions (office_id, report_type, updated_at desc);

create table workflow_definition_revisions (
    id bigserial primary key,
    workflow_definition_id bigint not null references workflow_definitions(id),
    version integer not null,
    status text not null,
    definition_json jsonb not null default '{}'::jsonb,
    created_by bigint references users(id),
    published_by bigint references users(id),
    created_at timestamptz not null,
    published_at timestamptz,
    unique (workflow_definition_id, version)
);

create index ix_workflow_definition_revisions_definition
    on workflow_definition_revisions (workflow_definition_id, version desc);

create index ix_workflow_definition_revisions_status
    on workflow_definition_revisions (status, published_at desc);

create table rule_sets (
    id bigserial primary key,
    office_id bigint references offices(id),
    rule_set_code text not null,
    name text not null,
    report_type text,
    status text not null,
    created_by bigint references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index ux_rule_sets_scope_report_code
    on rule_sets (coalesce(office_id, -1), coalesce(report_type, ''), rule_set_code);

create index ix_rule_sets_office_report
    on rule_sets (office_id, report_type, updated_at desc);

create table rule_set_revisions (
    id bigserial primary key,
    rule_set_id bigint not null references rule_sets(id),
    version integer not null,
    status text not null,
    rules_json jsonb not null default '{}'::jsonb,
    created_by bigint references users(id),
    published_by bigint references users(id),
    created_at timestamptz not null,
    published_at timestamptz,
    unique (rule_set_id, version)
);

create index ix_rule_set_revisions_rule_set
    on rule_set_revisions (rule_set_id, version desc);

create index ix_rule_set_revisions_status
    on rule_set_revisions (status, published_at desc);

create table output_layout_configs (
    id bigserial primary key,
    office_id bigint references offices(id),
    layout_code text not null,
    name text not null,
    report_type text,
    status text not null,
    created_by bigint references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index ux_output_layout_configs_scope_report_code
    on output_layout_configs (coalesce(office_id, -1), coalesce(report_type, ''), layout_code);

create index ix_output_layout_configs_office_report
    on output_layout_configs (office_id, report_type, updated_at desc);

create table output_layout_config_revisions (
    id bigserial primary key,
    output_layout_config_id bigint not null references output_layout_configs(id),
    version integer not null,
    status text not null,
    layout_json jsonb not null default '{}'::jsonb,
    created_by bigint references users(id),
    published_by bigint references users(id),
    created_at timestamptz not null,
    published_at timestamptz,
    unique (output_layout_config_id, version)
);

create index ix_output_layout_config_revisions_config
    on output_layout_config_revisions (output_layout_config_id, version desc);

create index ix_output_layout_config_revisions_status
    on output_layout_config_revisions (status, published_at desc);

create table office_config_overrides (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    report_type text not null,
    status text not null,
    template_revision_id bigint references document_template_revisions(id),
    workflow_revision_id bigint references workflow_definition_revisions(id),
    rule_set_revision_id bigint references rule_set_revisions(id),
    output_layout_revision_id bigint references output_layout_config_revisions(id),
    effective_from timestamptz,
    effective_to timestamptz,
    created_by bigint references users(id),
    updated_by bigint references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index ux_office_config_overrides_active
    on office_config_overrides (office_id, report_type)
    where status = 'ACTIVE';

create index ix_office_config_overrides_office_report
    on office_config_overrides (office_id, report_type, updated_at desc);

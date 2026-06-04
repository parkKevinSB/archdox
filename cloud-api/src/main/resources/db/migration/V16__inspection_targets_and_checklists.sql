create table inspection_targets (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    project_id bigint not null references projects(id),
    site_id bigint not null references sites(id),
    parent_target_id bigint references inspection_targets(id),
    target_type text not null,
    code text,
    name text not null,
    address text,
    metadata_json jsonb not null default '{}'::jsonb,
    status text not null,
    created_by bigint references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index ix_inspection_targets_office_site_status
    on inspection_targets (office_id, site_id, status);
create index ix_inspection_targets_parent
    on inspection_targets (parent_target_id);

create table inspection_report_targets (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    report_id bigint not null references inspection_reports(id),
    target_id bigint not null references inspection_targets(id),
    role text not null,
    snapshot_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    unique (report_id, target_id, role)
);

create index ix_inspection_report_targets_report
    on inspection_report_targets (report_id, role);

create table checklist_schemas (
    id bigserial primary key,
    office_id bigint references offices(id),
    report_type text not null,
    site_type text,
    target_type text,
    code text not null,
    name text not null,
    version integer not null,
    status text not null,
    schema_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index ix_checklist_schemas_resolution
    on checklist_schemas (office_id, report_type, site_type, target_type, status);

create table checklist_items (
    id bigserial primary key,
    checklist_schema_id bigint not null references checklist_schemas(id),
    item_code text not null,
    label text not null,
    description text,
    answer_type text not null,
    required boolean not null default false,
    display_order integer not null,
    options_json jsonb not null default '[]'::jsonb,
    unique (checklist_schema_id, item_code)
);

create index ix_checklist_items_schema_order
    on checklist_items (checklist_schema_id, display_order);

create table inspection_checklist_answers (
    id bigserial primary key,
    office_id bigint not null references offices(id),
    report_id bigint not null references inspection_reports(id),
    checklist_schema_id bigint not null references checklist_schemas(id),
    checklist_item_id bigint not null references checklist_items(id),
    target_id bigint references inspection_targets(id),
    answer_value_json jsonb not null default '{}'::jsonb,
    note text,
    client_revision integer not null default 1,
    saved_by bigint references users(id),
    saved_at timestamptz not null,
    unique (report_id, checklist_item_id, target_id)
);

create index ix_inspection_checklist_answers_report
    on inspection_checklist_answers (report_id, checklist_schema_id);

insert into checklist_schemas (
    office_id,
    report_type,
    site_type,
    target_type,
    code,
    name,
    version,
    status,
    schema_json,
    created_at,
    updated_at
) values
    (null, 'DAILY_SUPERVISION', null, null, 'DAILY_SUPERVISION_DEFAULT', '감리일지 기본 체크리스트', 1, 'ACTIVE', '{}'::jsonb, now(), now()),
    (null, 'PERIODIC_SAFETY', null, null, 'PERIODIC_SAFETY_DEFAULT', '정기 안전점검 기본 체크리스트', 1, 'ACTIVE', '{}'::jsonb, now(), now()),
    (null, 'FACILITY_CHECK', null, null, 'FACILITY_CHECK_DEFAULT', '시설 점검 기본 체크리스트', 1, 'ACTIVE', '{}'::jsonb, now(), now());

insert into checklist_items (
    checklist_schema_id,
    item_code,
    label,
    description,
    answer_type,
    required,
    display_order,
    options_json
)
select id, item_code, label, description, answer_type, required, display_order, options_json
from checklist_schemas schema
cross join (
    values
        ('WORK_STATUS', '작업 진행 상태', '현장 작업이 예정대로 진행되었는지 확인합니다.', 'SELECT', true, 10, '["양호","주의","미흡"]'::jsonb),
        ('SAFETY_ISSUE', '안전상 특이사항', '안전 관련 조치가 필요한 항목을 기록합니다.', 'SELECT', true, 20, '["없음","관찰","조치필요"]'::jsonb),
        ('PHOTO_REQUIRED', '사진 기록 필요', '문서에 포함할 사진 기록이 필요한지 확인합니다.', 'YES_NO', false, 30, '[]'::jsonb),
        ('REMARKS', '비고', '문서에 반영할 추가 특이사항입니다.', 'TEXT', false, 40, '[]'::jsonb)
) as items(item_code, label, description, answer_type, required, display_order, options_json)
where schema.code = 'DAILY_SUPERVISION_DEFAULT';

insert into checklist_items (
    checklist_schema_id,
    item_code,
    label,
    description,
    answer_type,
    required,
    display_order,
    options_json
)
select id, item_code, label, description, answer_type, required, display_order, options_json
from checklist_schemas schema
cross join (
    values
        ('CRACK_CHECK', '균열 여부', '주요 구조부 균열 또는 의심 흔적을 확인합니다.', 'SELECT', true, 10, '["이상없음","관찰","조치필요"]'::jsonb),
        ('LEAK_CHECK', '누수 흔적', '천장, 벽체, 설비 주변 누수 흔적을 확인합니다.', 'SELECT', true, 20, '["이상없음","관찰","조치필요"]'::jsonb),
        ('EVACUATION_ROUTE', '피난통로 적치물', '피난통로 장애물 또는 적치물을 확인합니다.', 'SELECT', true, 30, '["이상없음","관찰","조치필요"]'::jsonb),
        ('SAFETY_REMARKS', '점검 특이사항', '안전점검 문서에 반영할 특이사항입니다.', 'TEXT', false, 40, '[]'::jsonb)
) as items(item_code, label, description, answer_type, required, display_order, options_json)
where schema.code = 'PERIODIC_SAFETY_DEFAULT';

insert into checklist_items (
    checklist_schema_id,
    item_code,
    label,
    description,
    answer_type,
    required,
    display_order,
    options_json
)
select id, item_code, label, description, answer_type, required, display_order, options_json
from checklist_schemas schema
cross join (
    values
        ('FACILITY_STATUS', '시설 상태', '대상 시설의 전반적인 상태를 확인합니다.', 'SELECT', true, 10, '["양호","주의","미흡"]'::jsonb),
        ('MAINTENANCE_REQUIRED', '유지보수 필요', '유지보수 또는 교체가 필요한지 확인합니다.', 'YES_NO', true, 20, '[]'::jsonb),
        ('RISK_NOTE', '위험 요인', '운영/안전상 위험 요인을 기록합니다.', 'TEXT', false, 30, '[]'::jsonb),
        ('NEXT_ACTION', '다음 조치', '후속 조치나 재점검 계획을 기록합니다.', 'TEXT', false, 40, '[]'::jsonb)
) as items(item_code, label, description, answer_type, required, display_order, options_json)
where schema.code = 'FACILITY_CHECK_DEFAULT';

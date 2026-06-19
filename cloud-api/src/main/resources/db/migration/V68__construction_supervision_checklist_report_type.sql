insert into document_type_definitions (
    office_id,
    code,
    report_type,
    name,
    description,
    category,
    default_template_code,
    default_template_storage_ref,
    checklist_schema_code,
    default_output_format,
    workflow_json,
    output_layout_json,
    active,
    display_order,
    created_at,
    updated_at
)
values (
    null,
    'CONSTRUCTION_SUPERVISION_CHECKLIST',
    'CONSTRUCTION_SUPERVISION_CHECKLIST',
    '감리 체크리스트',
    '현장에 작성된 공사감리일지를 기준으로 공종별, 단계별 또는 전체 감리 체크리스트를 생성합니다.',
    'CONSTRUCTION_SUPERVISION',
    null,
    null,
    null,
    'DOCX',
    '{
      "flowId": "construction-supervision-checklist-writing",
      "title": "감리 체크리스트 작성",
      "steps": [
        {
          "code": "CHECKLIST_SOURCE",
          "title": "체크리스트 범위",
          "description": "현재 현장의 감리일지를 기준으로 출력 종류와 대상 기간 또는 감리일지를 선택합니다.",
          "stepType": "CHECKLIST_SOURCE",
          "fields": [
            {"key": "checklistSelection", "label": "체크리스트 선택 조건", "type": "json", "required": true}
          ]
        }
      ]
    }'::jsonb,
    '{}'::jsonb,
    true,
    45,
    now(),
    now()
)
on conflict (code) where office_id is null do update
set report_type = excluded.report_type,
    name = excluded.name,
    description = excluded.description,
    category = excluded.category,
    default_template_code = excluded.default_template_code,
    default_template_storage_ref = excluded.default_template_storage_ref,
    checklist_schema_code = excluded.checklist_schema_code,
    default_output_format = excluded.default_output_format,
    workflow_json = excluded.workflow_json,
    output_layout_json = excluded.output_layout_json,
    active = excluded.active,
    display_order = excluded.display_order,
    updated_at = now();

create table document_type_definitions (
    id bigserial primary key,
    office_id bigint references offices(id),
    code text not null,
    report_type text not null,
    name text not null,
    description text,
    category text not null,
    default_template_code text,
    default_template_storage_ref text,
    checklist_schema_code text,
    default_output_format text not null default 'DOCX',
    workflow_json jsonb not null default '{}'::jsonb,
    output_layout_json jsonb not null default '{}'::jsonb,
    active boolean not null default true,
    display_order integer not null default 1000,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index ux_document_type_definitions_global_code
    on document_type_definitions (code)
    where office_id is null;

create unique index ux_document_type_definitions_office_code
    on document_type_definitions (office_id, code)
    where office_id is not null;

create index ix_document_type_definitions_visible
    on document_type_definitions (office_id, active, display_order);

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
) values
    (
        null,
        'DEMOLITION_SAFETY_CHECKLIST',
        'DEMOLITION_SAFETY_CHECKLIST',
        '해체공사 안전점검표',
        '해체공사 작업 전후 안전점검 항목을 확인하고 조치사항을 기록합니다.',
        'DEMOLITION_SUPERVISION',
        'KOREAN_DEMOLITION_SAFETY_CHECKLIST_APPENDIX_1',
        'templates/korean/korean-demolition-safety-checklist-appendix-1.docx',
        'DEMOLITION_SAFETY_CHECKLIST_DEFAULT',
        'DOCX',
        '{
          "flowId": "demolition-safety-checklist-writing",
          "title": "해체공사 안전점검표 작성",
          "steps": [
            {
              "code": "BASIC_INFO",
              "title": "기본정보",
              "description": "점검일자, 점검위치, 감리자와 작업단계를 기록합니다.",
              "stepType": "FORM",
              "fields": [
                {"key": "inspectionDate", "label": "점검일자", "type": "date", "required": true},
                {"key": "location", "label": "점검위치", "type": "text", "required": true},
                {"key": "supervisorName", "label": "감리자", "type": "text", "required": true},
                {"key": "demolitionWorkerName", "label": "해체작업자", "type": "text", "required": false},
                {"key": "stage", "label": "작업단계", "type": "text", "required": true}
              ]
            },
            {
              "code": "CHECKLIST",
              "title": "안전점검",
              "description": "해체 순서, 보강, 낙하물 방지, 장비 동선 등 기본 안전항목을 확인합니다.",
              "stepType": "CHECKLIST",
              "fields": []
            },
            {
              "code": "PHOTOS",
              "title": "현장사진",
              "description": "점검 항목과 관련된 사진을 첨부합니다.",
              "stepType": "PHOTO",
              "fields": []
            },
            {
              "code": "ISSUES",
              "title": "조치사항",
              "description": "부적합 사항, 보완 요청, 조치 결과를 정리합니다.",
              "stepType": "FORM",
              "fields": [
                {"key": "correctiveAction", "label": "조치사항", "type": "textarea", "required": false},
                {"key": "issueAndAction", "label": "지적사항 및 처리결과", "type": "textarea", "required": false}
              ]
            }
          ]
        }'::jsonb,
        '{
          "sections": [
            {
              "key": "safetyChecklistSection",
              "type": "CHECKLIST_TABLE",
              "includeTitle": false,
              "tableStyle": "ArchDoxInspectionTable",
              "headerFill": "E8EEF5",
              "borderColor": "5E6A75",
              "fields": [
                {"label": "점검항목", "source": "label", "width": "3400"},
                {"label": "검사기준", "source": "answer.criteria", "width": "2600"},
                {"label": "결과", "source": "answer.value", "width": "1600"},
                {"label": "조치사항", "source": "note", "width": "2600"}
              ]
            }
          ]
        }'::jsonb,
        true,
        10,
        now(),
        now()
    ),
    (
        null,
        'DEMOLITION_DAILY_SUPERVISION_LOG',
        'DEMOLITION_DAILY_SUPERVISION_LOG',
        '해체공사 감리일지',
        '해체공사 일일 감리내용과 특기사항을 기록합니다.',
        'DEMOLITION_SUPERVISION',
        'KOREAN_DEMOLITION_DAILY_SUPERVISION_LOG_APPENDIX_2',
        'templates/korean/korean-demolition-daily-supervision-log-appendix-2.docx',
        'DEMOLITION_DAILY_SUPERVISION_LOG_DEFAULT',
        'DOCX',
        '{
          "flowId": "demolition-daily-supervision-log-writing",
          "title": "해체공사 감리일지 작성",
          "steps": [
            {
              "code": "BASIC_INFO",
              "title": "기본정보",
              "description": "공사일자, 날씨, 감리자 정보를 기록합니다.",
              "stepType": "FORM",
              "fields": [
                {"key": "inspectionDate", "label": "공사일자", "type": "date", "required": true},
                {"key": "weather", "label": "날씨", "type": "text", "required": false},
                {"key": "supervisorName", "label": "공사감리자", "type": "text", "required": true},
                {"key": "inspectorName", "label": "감리자", "type": "text", "required": false}
              ]
            },
            {
              "code": "DEMOLITION_DAILY_LOG",
              "title": "감리내용",
              "description": "작업사항, 공종별 감리 착안사항과 감리내용을 기록합니다.",
              "stepType": "FORM",
              "fields": [
                {"key": "workDescription", "label": "작업사항", "type": "textarea", "required": false},
                {"key": "specialNotes", "label": "특기사항", "type": "textarea", "required": false},
                {"key": "issueAndAction", "label": "지적사항 및 처리결과", "type": "textarea", "required": false}
              ]
            },
            {"code": "CHECKLIST", "title": "감리 체크", "description": "주요 감리 항목을 확인합니다.", "stepType": "CHECKLIST", "fields": []},
            {"code": "PHOTOS", "title": "현장사진", "description": "감리내용에 필요한 사진을 첨부합니다.", "stepType": "PHOTO", "fields": []}
          ]
        }'::jsonb,
        '{
          "sections": [
            {
              "key": "supervisionItemsSection",
              "type": "CHECKLIST_TABLE",
              "includeTitle": false,
              "tableStyle": "ArchDoxInspectionTable",
              "headerFill": "E8EEF5",
              "borderColor": "5E6A75",
              "fields": [
                {"label": "공종", "source": "answer.trade", "width": "2600"},
                {"label": "감리 항목", "source": "label", "width": "2800"},
                {"label": "감리내용", "source": "note", "width": "4200"}
              ]
            }
          ]
        }'::jsonb,
        true,
        20,
        now(),
        now()
    ),
    (
        null,
        'DEMOLITION_COMPLETION_REPORT',
        'DEMOLITION_COMPLETION_REPORT',
        '해체감리 완료보고서',
        '해체감리 수행 결과와 감리자 배치현황을 정리합니다.',
        'DEMOLITION_SUPERVISION',
        'KOREAN_DEMOLITION_COMPLETION_REPORT_APPENDIX_3',
        'templates/korean/korean-demolition-completion-report-appendix-3.docx',
        'DEMOLITION_COMPLETION_REPORT_DEFAULT',
        'DOCX',
        '{
          "flowId": "demolition-completion-report-writing",
          "title": "해체감리 완료보고서 작성",
          "steps": [
            {"code": "BASIC_INFO", "title": "기본정보", "description": "감리자, 공사시공자, 용역 정보를 기록합니다.", "stepType": "FORM", "fields": [
              {"key": "supervisorName", "label": "감리자", "type": "text", "required": true},
              {"key": "supervisorOfficeName", "label": "감리자 사무소", "type": "text", "required": false},
              {"key": "contractorName", "label": "공사시공자", "type": "text", "required": false},
              {"key": "serviceName", "label": "용역명", "type": "text", "required": true}
            ]},
            {"code": "SUPERVISOR_DEPLOYMENT", "title": "감리자 배치", "description": "감리자 배치현황을 기록합니다.", "stepType": "CHECKLIST", "fields": []},
            {"code": "REMARKS", "title": "종합의견", "description": "종합의견과 제출일을 기록합니다.", "stepType": "FORM", "fields": [
              {"key": "comprehensiveOpinion", "label": "종합의견", "type": "textarea", "required": false},
              {"key": "reportDate", "label": "제출일", "type": "date", "required": false}
            ]}
          ]
        }'::jsonb,
        '{
          "sections": [
            {
              "key": "supervisorDeploymentSection",
              "type": "CHECKLIST_TABLE",
              "includeTitle": false,
              "tableStyle": "ArchDoxInspectionTable",
              "headerFill": "E8EEF5",
              "borderColor": "5E6A75",
              "fields": [
                {"label": "구분", "source": "itemCode", "width": "1800"},
                {"label": "성명", "source": "label", "width": "2600"},
                {"label": "담당업무", "source": "answer.value", "width": "2600"},
                {"label": "비고", "source": "note", "width": "2600"}
              ]
            }
          ]
        }'::jsonb,
        true,
        30,
        now(),
        now()
    ),
    (
        null,
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        '공사감리일지',
        '공사감리 일일 감리내용과 지적사항을 기록합니다.',
        'CONSTRUCTION_SUPERVISION',
        'KOREAN_CONSTRUCTION_DAILY_SUPERVISION_LOG_APPENDIX_2',
        'templates/korean/korean-construction-daily-supervision-log-appendix-2.docx',
        'CONSTRUCTION_DAILY_SUPERVISION_LOG_DEFAULT',
        'DOCX',
        '{
          "flowId": "construction-daily-supervision-log-writing",
          "title": "공사감리일지 작성",
          "steps": [
            {"code": "BASIC_INFO", "title": "기본정보", "description": "공사일자, 날씨, 감리자를 기록합니다.", "stepType": "FORM", "fields": [
              {"key": "inspectionDate", "label": "공사일자", "type": "date", "required": true},
              {"key": "weather", "label": "날씨", "type": "text", "required": false},
              {"key": "chiefSupervisorName", "label": "총괄감리책임자", "type": "text", "required": true},
              {"key": "architectAssistantName", "label": "건축사보", "type": "text", "required": false}
            ]},
            {"code": "DAILY_LOG", "title": "감리내용", "description": "공종 및 세부공정별 감리내용을 기록합니다.", "stepType": "FORM", "fields": [
              {"key": "specialNotes", "label": "특기사항", "type": "textarea", "required": false},
              {"key": "issueAndAction", "label": "지적사항 및 처리결과", "type": "textarea", "required": false}
            ]},
            {"code": "CHECKLIST", "title": "감리 체크", "description": "주요 감리 항목을 확인합니다.", "stepType": "CHECKLIST", "fields": []},
            {"code": "PHOTOS", "title": "현장사진", "description": "감리내용에 필요한 사진을 첨부합니다.", "stepType": "PHOTO", "fields": []}
          ]
        }'::jsonb,
        '{
          "sections": [
            {
              "key": "supervisionItemsSection",
              "type": "CHECKLIST_TABLE",
              "includeTitle": false,
              "tableStyle": "ArchDoxInspectionTable",
              "headerFill": "E8EEF5",
              "borderColor": "5E6A75",
              "fields": [
                {"label": "공종 및 세부공정", "source": "answer.trade", "width": "2800"},
                {"label": "감리 항목", "source": "label", "width": "2600"},
                {"label": "감리내용", "source": "note", "width": "4200"}
              ]
            }
          ]
        }'::jsonb,
        true,
        40,
        now(),
        now()
    ),
    (
        null,
        'CONSTRUCTION_SUPERVISION_REPORT',
        'CONSTRUCTION_SUPERVISION_REPORT',
        '감리보고서',
        '공사감리 중간/완료 보고와 관계전문기술자 의견을 정리합니다.',
        'CONSTRUCTION_SUPERVISION',
        'KOREAN_CONSTRUCTION_SUPERVISION_REPORT_APPENDIX_1',
        'templates/korean/korean-construction-supervision-report-appendix-1.docx',
        'CONSTRUCTION_SUPERVISION_REPORT_DEFAULT',
        'DOCX',
        '{
          "flowId": "construction-supervision-report-writing",
          "title": "감리보고서 작성",
          "steps": [
            {"code": "BASIC_INFO", "title": "기본정보", "description": "허가번호, 대지위치, 공사명을 기록합니다.", "stepType": "FORM", "fields": [
              {"key": "permitNumber", "label": "허가번호", "type": "text", "required": false},
              {"key": "permitDate", "label": "허가일자", "type": "date", "required": false},
              {"key": "lotNumber", "label": "지번", "type": "text", "required": false}
            ]},
            {"code": "REPORT_OPINION", "title": "감리의견", "description": "관계전문기술자 의견과 종합의견을 기록합니다.", "stepType": "CHECKLIST", "fields": []},
            {"code": "REMARKS", "title": "첨부/특기사항", "description": "첨부자료와 특기사항을 정리합니다.", "stepType": "FORM", "fields": [
              {"key": "relationEngineerOpinion", "label": "관계전문기술자 의견", "type": "textarea", "required": false},
              {"key": "comprehensiveOpinion", "label": "종합의견", "type": "textarea", "required": false},
              {"key": "specialNotes", "label": "특기사항", "type": "textarea", "required": false}
            ]}
          ]
        }'::jsonb,
        '{
          "sections": [
            {
              "key": "reportOpinionSection",
              "type": "CHECKLIST_TABLE",
              "includeTitle": false,
              "tableStyle": "ArchDoxInspectionTable",
              "headerFill": "E8EEF5",
              "borderColor": "5E6A75",
              "fields": [
                {"label": "구분", "source": "itemCode", "width": "1800"},
                {"label": "확인내용", "source": "label", "width": "3600"},
                {"label": "결과", "source": "answer.value", "width": "1600"},
                {"label": "의견", "source": "note", "width": "3000"}
              ]
            }
          ]
        }'::jsonb,
        true,
        50,
        now(),
        now()
    ),
    (
        null,
        'DAILY_SUPERVISION',
        'DAILY_SUPERVISION',
        '감리일지',
        '기존 감리일지 호환 문서 타입입니다.',
        'LEGACY',
        'KOREAN_CONSTRUCTION_DAILY_SUPERVISION_LOG_APPENDIX_2',
        'templates/korean/korean-construction-daily-supervision-log-appendix-2.docx',
        'DAILY_SUPERVISION_DEFAULT',
        'DOCX',
        '{"flowId": "daily-supervision-writing", "title": "감리일지 작성"}'::jsonb,
        '{
          "sections": [
            {
              "key": "supervisionItemsSection",
              "type": "CHECKLIST_TABLE",
              "includeTitle": false,
              "tableStyle": "ArchDoxInspectionTable",
              "headerFill": "E8EEF5",
              "borderColor": "5E6A75"
            }
          ]
        }'::jsonb,
        true,
        900,
        now(),
        now()
    ),
    (
        null,
        'PERIODIC_SAFETY',
        'PERIODIC_SAFETY',
        '정기 안전점검',
        '기존 정기 안전점검 호환 문서 타입입니다.',
        'LEGACY',
        null,
        null,
        'PERIODIC_SAFETY_DEFAULT',
        'DOCX',
        '{"flowId": "periodic-safety-writing", "title": "정기 안전점검 작성"}'::jsonb,
        '{}'::jsonb,
        true,
        910,
        now(),
        now()
    ),
    (
        null,
        'FACILITY_CHECK',
        'FACILITY_CHECK',
        '시설 점검',
        '기존 시설 점검 호환 문서 타입입니다.',
        'LEGACY',
        null,
        null,
        'FACILITY_CHECK_DEFAULT',
        'DOCX',
        '{"flowId": "facility-check-writing", "title": "시설 점검 작성"}'::jsonb,
        '{}'::jsonb,
        true,
        920,
        now(),
        now()
    );

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
    (null, 'DEMOLITION_SAFETY_CHECKLIST', null, null, 'DEMOLITION_SAFETY_CHECKLIST_DEFAULT', '해체공사 안전점검표 기본 체크리스트', 1, 'ACTIVE', '{"documentType":"DEMOLITION_SAFETY_CHECKLIST"}'::jsonb, now(), now()),
    (null, 'DEMOLITION_DAILY_SUPERVISION_LOG', null, null, 'DEMOLITION_DAILY_SUPERVISION_LOG_DEFAULT', '해체공사 감리일지 기본 체크리스트', 1, 'ACTIVE', '{"documentType":"DEMOLITION_DAILY_SUPERVISION_LOG"}'::jsonb, now(), now()),
    (null, 'DEMOLITION_COMPLETION_REPORT', null, null, 'DEMOLITION_COMPLETION_REPORT_DEFAULT', '해체감리 완료보고서 기본 체크리스트', 1, 'ACTIVE', '{"documentType":"DEMOLITION_COMPLETION_REPORT"}'::jsonb, now(), now()),
    (null, 'CONSTRUCTION_DAILY_SUPERVISION_LOG', null, null, 'CONSTRUCTION_DAILY_SUPERVISION_LOG_DEFAULT', '공사감리일지 기본 체크리스트', 1, 'ACTIVE', '{"documentType":"CONSTRUCTION_DAILY_SUPERVISION_LOG"}'::jsonb, now(), now()),
    (null, 'CONSTRUCTION_SUPERVISION_REPORT', null, null, 'CONSTRUCTION_SUPERVISION_REPORT_DEFAULT', '감리보고서 기본 체크리스트', 1, 'ACTIVE', '{"documentType":"CONSTRUCTION_SUPERVISION_REPORT"}'::jsonb, now(), now());

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
select schema.id, item_code, label, description, answer_type, required, display_order, options_json
from checklist_schemas schema
cross join (
    values
        ('DEMOLITION_SEQUENCE', '해체 순서 준수', '해체계획서의 작업순서와 실제 작업순서가 일치하는지 확인합니다.', 'SELECT', true, 10, '["적합","보완필요","부적합","해당없음"]'::jsonb),
        ('STRUCTURAL_SUPPORT', '구조 보강 상태', '잭서포트, 가설보강, 보강재 설치 상태와 간격을 확인합니다.', 'SELECT', true, 20, '["적합","보완필요","부적합","해당없음"]'::jsonb),
        ('FALLING_OBJECT_PREVENTION', '낙하물 방지 조치', '방호선반, 낙하물 방지망, 개구부 덮개 등 추락/낙하 방지 조치를 확인합니다.', 'SELECT', true, 30, '["적합","보완필요","부적합","해당없음"]'::jsonb),
        ('EQUIPMENT_ROUTE', '장비 이동 동선 확보', '장비 이동구간, 회전반경, 보행자 분리 상태를 확인합니다.', 'SELECT', true, 40, '["적합","보완필요","부적합","해당없음"]'::jsonb),
        ('DEBRIS_LOADING', '잔재물 적치 및 하중 관리', '해체 잔재물 적치 높이와 슬래브 하중 초과 여부를 확인합니다.', 'SELECT', true, 50, '["적합","보완필요","부적합","해당없음"]'::jsonb),
        ('WORKER_PPE', '작업자 보호구 착용', '안전모, 안전대, 방진마스크 등 보호구 착용 상태를 확인합니다.', 'SELECT', true, 60, '["적합","보완필요","부적합","해당없음"]'::jsonb),
        ('SITE_ACCESS_CONTROL', '출입 통제 및 보행자 안전', '출입통제선, 안내표지, 보행자 안전조치 상태를 확인합니다.', 'SELECT', true, 70, '["적합","보완필요","부적합","해당없음"]'::jsonb),
        ('DUST_NOISE_FIRE', '분진/소음/화재 관리', '살수, 방음, 소화기, 화기관리 등 주변환경 및 화재 안전조치를 확인합니다.', 'SELECT', false, 80, '["적합","보완필요","부적합","해당없음"]'::jsonb)
) as items(item_code, label, description, answer_type, required, display_order, options_json)
where schema.code = 'DEMOLITION_SAFETY_CHECKLIST_DEFAULT';

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
select schema.id, item_code, label, description, answer_type, required, display_order, options_json
from checklist_schemas schema
cross join (
    values
        ('WORK_SCOPE', '금일 작업범위 확인', '금일 해체 작업범위와 계획 대비 진행상태를 기록합니다.', 'SELECT', true, 10, '["정상","주의","보완필요"]'::jsonb),
        ('SUPERVISION_FOCUS', '감리 착안사항', '공종별 주요 감리 착안사항을 확인합니다.', 'TEXT', true, 20, '[]'::jsonb),
        ('SAFETY_ACTION', '안전조치 이행상태', '작업 전 안전교육, 통제, 보호구, 보강 등 안전조치 이행상태를 확인합니다.', 'SELECT', true, 30, '["적합","보완필요","부적합"]'::jsonb),
        ('ISSUE_RESULT', '지적사항 처리결과', '감리자가 지적한 사항과 처리 결과를 기록합니다.', 'TEXT', false, 40, '[]'::jsonb)
) as items(item_code, label, description, answer_type, required, display_order, options_json)
where schema.code = 'DEMOLITION_DAILY_SUPERVISION_LOG_DEFAULT';

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
select schema.id, item_code, label, description, answer_type, required, display_order, options_json
from checklist_schemas schema
cross join (
    values
        ('SUPERVISOR_DEPLOYED', '감리자 배치 확인', '계약 및 법정 기준에 따른 감리자 배치 여부를 확인합니다.', 'SELECT', true, 10, '["확인","보완필요","해당없음"]'::jsonb),
        ('SAFETY_CHECK_ATTACHED', '안전점검표 첨부', '해체공사 안전점검표 첨부 여부를 확인합니다.', 'YES_NO', true, 20, '[]'::jsonb),
        ('DAILY_LOG_ATTACHED', '감리업무일지 첨부', '감리업무일지 첨부 여부를 확인합니다.', 'YES_NO', true, 30, '[]'::jsonb),
        ('COMPLETION_OPINION', '완료 종합의견', '해체감리 완료에 대한 종합의견을 기록합니다.', 'TEXT', false, 40, '[]'::jsonb)
) as items(item_code, label, description, answer_type, required, display_order, options_json)
where schema.code = 'DEMOLITION_COMPLETION_REPORT_DEFAULT';

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
select schema.id, item_code, label, description, answer_type, required, display_order, options_json
from checklist_schemas schema
cross join (
    values
        ('STRUCTURE_WORK', '구조공사 감리내용', '철근, 거푸집, 콘크리트 등 주요 구조공사 감리내용을 기록합니다.', 'TEXT', true, 10, '[]'::jsonb),
        ('MATERIAL_CHECK', '자재 및 시험 확인', '반입자재, 규격, 시험성적서, 검측 여부를 확인합니다.', 'SELECT', false, 20, '["확인","보완필요","해당없음"]'::jsonb),
        ('SITE_SAFETY', '현장 안전상태', '작업발판, 추락방지, 통행동선 등 현장 안전상태를 확인합니다.', 'SELECT', false, 30, '["적합","보완필요","부적합"]'::jsonb),
        ('INSTRUCTION_RESULT', '지적사항 및 처리결과', '재시공, 공사중지 등 지시내용과 처리결과를 기록합니다.', 'TEXT', false, 40, '[]'::jsonb)
) as items(item_code, label, description, answer_type, required, display_order, options_json)
where schema.code = 'CONSTRUCTION_DAILY_SUPERVISION_LOG_DEFAULT';

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
select schema.id, item_code, label, description, answer_type, required, display_order, options_json
from checklist_schemas schema
cross join (
    values
        ('FIELD_INVESTIGATION', '현장 조사 및 법령 기준 확인', '대지, 도로, 구조, 피난, 방화 등 주요 법령 기준 확인내용을 기록합니다.', 'TEXT', true, 10, '[]'::jsonb),
        ('ENGINEER_OPINION', '관계전문기술자 의견 확인', '관계전문기술자 의견 또는 확인서를 검토했는지 확인합니다.', 'SELECT', false, 20, '["확인","보완필요","해당없음"]'::jsonb),
        ('SUPERVISION_OPINION', '공사감리자 종합의견', '공사감리자의 종합의견을 기록합니다.', 'TEXT', true, 30, '[]'::jsonb),
        ('ATTACHMENTS', '첨부자료 확인', '사진, 시험성적서, 지시공문 등 첨부자료를 확인합니다.', 'SELECT', false, 40, '["확인","보완필요","해당없음"]'::jsonb)
) as items(item_code, label, description, answer_type, required, display_order, options_json)
where schema.code = 'CONSTRUCTION_SUPERVISION_REPORT_DEFAULT';

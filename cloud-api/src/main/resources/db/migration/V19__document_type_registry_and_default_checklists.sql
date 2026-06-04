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
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        '공사감리일지',
        '공사감리 일일 감리내용과 지적사항을 기록합니다.',
        'CONSTRUCTION_SUPERVISION',
        'KOREAN_CONSTRUCTION_DAILY_SUPERVISION_LOG_APPENDIX_2',
        'templates/korean/korean-construction-daily-supervision-log-appendix-2.docx',
        'CONSTRUCTION_DAILY_SUPERVISION_LOG_DEFAULT',
        'DOCX',
        $json$
        {
          "flowId": "construction-daily-supervision-log-writing",
          "title": "공사감리일지 작성",
          "steps": [
            {
              "code": "BASIC_INFO",
              "title": "기본 정보",
              "description": "공사일자, 날씨, 총괄감리책임자, 건축사보를 기록합니다.",
              "stepType": "FORM",
              "fields": [
                {"key": "inspectionDate", "label": "공사일자", "type": "date", "required": true},
                {"key": "weather", "label": "날씨", "type": "text", "required": false},
                {"key": "chiefSupervisorName", "label": "총괄감리책임자", "type": "text", "required": true},
                {"key": "architectAssistantName", "label": "건축사보", "type": "text", "required": false}
              ]
            },
            {
              "code": "DAILY_LOG",
              "title": "공종별 검사항목",
              "description": "공종, 세부공정, 층, 검사항목별 감리내용과 사진을 기록합니다.",
              "stepType": "DAILY_SUPERVISION_ITEMS",
              "fields": [
                {"key": "dailyItems", "label": "공종별 검사항목 데이터", "type": "json", "required": true}
              ]
            },
            {"code": "PHOTOS", "title": "현장 사진", "description": "감리내용과 연결되는 사진을 첨부합니다.", "stepType": "PHOTO", "fields": []},
            {
              "code": "REMARKS",
              "title": "특기사항과 지적사항",
              "description": "특기사항, 지적사항 및 처리결과, 다음 조치를 기록합니다.",
              "stepType": "FORM",
              "fields": [
                {"key": "specialNotes", "label": "특기사항", "type": "textarea", "required": false},
                {"key": "issueAndAction", "label": "지적사항 및 처리결과", "type": "textarea", "required": false},
                {"key": "nextAction", "label": "다음 조치", "type": "textarea", "required": false}
              ]
            }
          ]
        }
        $json$::jsonb,
        $json$
        {
          "sections": [
            {
              "key": "dailySupervisionItemsSection",
              "type": "DAILY_SUPERVISION_ITEMS_TABLE",
              "includeTitle": false,
              "tableStyle": "KoreanOfficialDailySupervisionTable",
              "fields": [
                {"label": "공종 및 세부공정", "source": "tradeProcessFloor", "width": "2800"},
                {"label": "감리 항목", "source": "inspectionItemName", "width": "3000"},
                {"label": "감리내용", "source": "supervisionContent", "width": "5200"}
              ]
            },
            {
              "key": "photoSection",
              "type": "PHOTO_TABLE",
              "title": "현장 사진",
              "photosPerRow": 2,
              "imageSize": "MEDIUM",
              "tableStyle": "KoreanOfficialPhotoTable",
              "fields": [
                {"label": "설명", "source": "caption"},
                {"label": "검사항목", "source": "checklistItemLabel"}
              ]
            }
          ]
        }
        $json$::jsonb,
        true,
        10,
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
        $json$
        {
          "flowId": "construction-supervision-report-writing",
          "title": "감리보고서 작성",
          "steps": [
            {
              "code": "BASIC_INFO",
              "title": "기본 정보",
              "description": "허가번호, 허가일자, 대지위치, 감리기간을 기록합니다.",
              "stepType": "FORM",
              "fields": [
                {"key": "permitNumber", "label": "허가번호", "type": "text", "required": false},
                {"key": "permitDate", "label": "허가일자", "type": "date", "required": false},
                {"key": "lotNumber", "label": "지번", "type": "text", "required": false},
                {"key": "supervisionStartDate", "label": "감리 시작일", "type": "date", "required": false},
                {"key": "supervisionEndDate", "label": "감리 종료일", "type": "date", "required": false},
                {"key": "supervisorName", "label": "감리자", "type": "text", "required": true}
              ]
            },
            {"code": "REPORT_OPINION", "title": "감리 의견 체크", "description": "현장조사와 종합의견을 확인합니다.", "stepType": "CHECKLIST", "fields": []},
            {
              "code": "REMARKS",
              "title": "의견 및 특기사항",
              "description": "관계전문기술자 의견, 종합의견, 특기사항을 기록합니다.",
              "stepType": "FORM",
              "fields": [
                {"key": "relationEngineerOpinion", "label": "관계전문기술자 의견", "type": "textarea", "required": false},
                {"key": "comprehensiveOpinion", "label": "종합의견", "type": "textarea", "required": false},
                {"key": "specialNotes", "label": "특기사항", "type": "textarea", "required": false}
              ]
            },
            {"code": "PHOTOS", "title": "첨부 사진", "description": "감리보고서에 필요한 사진과 증빙자료를 첨부합니다.", "stepType": "PHOTO", "fields": []}
          ]
        }
        $json$::jsonb,
        $json$
        {
          "sections": [
            {
              "key": "reportOpinionSection",
              "type": "CHECKLIST_TABLE",
              "includeTitle": false,
              "tableStyle": "ArchDoxInspectionTable",
              "fields": [
                {"label": "구분", "source": "itemCode", "width": "1800"},
                {"label": "확인 내용", "source": "label", "width": "3600"},
                {"label": "결과", "source": "answer.value", "width": "1400"},
                {"label": "사진", "source": "photoCount", "width": "1000"},
                {"label": "의견", "source": "note", "width": "3000"}
              ]
            },
            {
              "key": "photoSection",
              "type": "PHOTO_TABLE",
              "title": "첨부 사진",
              "photosPerRow": 2,
              "imageSize": "MEDIUM",
              "tableStyle": "ArchDoxPhotoTable",
              "fields": [
                {"label": "설명", "source": "caption"},
                {"label": "단계", "source": "stepCode"}
              ]
            }
          ]
        }
        $json$::jsonb,
        true,
        20,
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
        ('STRUCTURE_WORK', '구조공사 감리내용', '구조공사 공종 및 세부공정의 감리내용을 기록합니다.', 'TEXT', true, 10, '[]'::jsonb),
        ('MATERIAL_CHECK', '자재 및 시험 확인', '자재 반입, 시험성적서, 품질 확인 내용을 기록합니다.', 'SELECT', false, 20, '["확인","보완필요","해당없음"]'::jsonb),
        ('SITE_SAFETY', '현장 안전상태', '현장 안전상태와 보완 필요사항을 기록합니다.', 'SELECT', false, 30, '["적합","보완필요","부적합"]'::jsonb),
        ('INSTRUCTION_RESULT', '지적사항 및 처리결과', '지적사항과 처리결과를 기록합니다.', 'TEXT', false, 40, '[]'::jsonb)
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
        ('FIELD_INVESTIGATION', '현장 조사 및 법령 기준 확인', '현장 조사 결과와 법령 기준 검토 내용을 기록합니다.', 'TEXT', true, 10, '[]'::jsonb),
        ('ENGINEER_OPINION', '관계전문기술자 의견 확인', '관계전문기술자 의견 검토 여부를 확인합니다.', 'SELECT', false, 20, '["확인","보완필요","해당없음"]'::jsonb),
        ('SUPERVISION_OPINION', '공사감리자 종합의견', '공사감리자의 종합의견을 기록합니다.', 'TEXT', true, 30, '[]'::jsonb),
        ('ATTACHMENTS', '첨부자료 확인', '감리보고서 첨부자료 준비 상태를 확인합니다.', 'SELECT', false, 40, '["확인","보완필요","해당없음"]'::jsonb)
) as items(item_code, label, description, answer_type, required, display_order, options_json)
where schema.code = 'CONSTRUCTION_SUPERVISION_REPORT_DEFAULT';

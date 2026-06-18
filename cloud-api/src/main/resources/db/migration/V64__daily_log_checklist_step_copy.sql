update document_type_definitions
set workflow_json = jsonb_set(
        jsonb_set(
                jsonb_set(
                        workflow_json,
                        '{steps,1,title}',
                        to_jsonb('검사항목'::text),
                        false),
                '{steps,1,description}',
                to_jsonb('공종별 또는 단계별 그룹을 추가하고, 세부업무와 검사항목별 결과 및 사진을 기록합니다.'::text),
                false),
        '{steps,1,fields,0,label}',
        to_jsonb('검사항목 데이터'::text),
        false),
    updated_at = now()
where code = 'CONSTRUCTION_DAILY_SUPERVISION_LOG'
  and workflow_json #>> '{steps,1,code}' = 'DAILY_LOG';

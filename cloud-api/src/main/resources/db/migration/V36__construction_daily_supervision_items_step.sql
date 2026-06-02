update document_type_definitions
set workflow_json = $json$
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
      "title": "감리 항목",
      "description": "공종과 세부공정/층을 선택하고 감리 항목, 감리내용, 사진을 항목별로 기록합니다.",
      "stepType": "DAILY_SUPERVISION_ITEMS",
      "fields": [
        {"key": "dailyItems", "label": "감리 항목 데이터", "type": "json", "required": true}
      ]
    },
    {
      "code": "PHOTOS",
      "title": "현장 사진",
      "description": "공종별 감리내용과 연결되는 사진, 또는 일지 전체에 포함할 현장 사진을 확인합니다.",
      "stepType": "PHOTO",
      "fields": []
    },
    {
      "code": "REMARKS",
      "title": "특기사항과 지적사항",
      "description": "특기사항, 지적사항 및 처리결과, 다음 조치 내용을 기록합니다.",
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
    updated_at = now()
where office_id is null
  and code = 'CONSTRUCTION_DAILY_SUPERVISION_LOG';

with document_type_updates(code, workflow_json, output_layout_json) as (
    values
        (
            'DEMOLITION_SAFETY_CHECKLIST',
            $json$
            {
              "flowId": "demolition-safety-checklist-writing",
              "title": "해체공사 안전점검표 작성",
              "steps": [
                {
                  "code": "BASIC_INFO",
                  "title": "기본 정보",
                  "description": "점검일자, 점검위치, 감리자, 작업단계를 기록합니다.",
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
                  "code": "DEMOLITION_SAFETY_CHECK",
                  "title": "안전점검 결과",
                  "description": "해체공사 단계별 점검기준, 결과, 즉시 조치사항을 정리합니다.",
                  "stepType": "FORM",
                  "fields": [
                    {"key": "inspectionCriteria", "label": "점검기준", "type": "textarea", "required": false},
                    {"key": "inspectionResult", "label": "점검결과", "type": "textarea", "required": false},
                    {"key": "correctiveAction", "label": "조치사항", "type": "textarea", "required": false}
                  ]
                },
                {
                  "code": "CHECKLIST",
                  "title": "체크리스트",
                  "description": "해체순서, 구조보강, 낙하물 방지, 장비동선, 작업자 보호구 등 핵심 안전 항목을 확인합니다.",
                  "stepType": "CHECKLIST",
                  "fields": []
                },
                {
                  "code": "PHOTOS",
                  "title": "사진 증거",
                  "description": "체크리스트 항목이나 현장 조치와 연결되는 사진을 첨부합니다.",
                  "stepType": "PHOTO",
                  "fields": []
                },
                {
                  "code": "ISSUES",
                  "title": "지적 및 후속 조치",
                  "description": "부적합 항목, 보완 요청, 처리 결과, 다음 조치 계획을 정리합니다.",
                  "stepType": "FORM",
                  "fields": [
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
                  "key": "safetyChecklistSection",
                  "type": "CHECKLIST_TABLE",
                  "includeTitle": false,
                  "tableStyle": "ArchDoxInspectionTable",
                  "headerFill": "E8EEF5",
                  "borderColor": "5E6A75",
                  "fields": [
                    {"label": "점검항목", "source": "label", "width": "3000"},
                    {"label": "점검기준", "source": "answer.criteria", "width": "2600"},
                    {"label": "결과", "source": "answer.value", "width": "1400"},
                    {"label": "사진", "source": "photoCount", "width": "1000"},
                    {"label": "조치사항", "source": "note", "width": "3000"}
                  ]
                },
                {
                  "key": "checklistPhotoSection",
                  "type": "CHECKLIST_PHOTO_TABLE",
                  "title": "점검 사진 증거",
                  "includeTitle": true,
                  "tableStyle": "ArchDoxInspectionTable",
                  "headerFill": "EFE7C6",
                  "borderColor": "8A7A46",
                  "fields": [
                    {"label": "항목코드", "source": "itemCode", "width": "1700"},
                    {"label": "점검항목", "source": "label", "width": "3600"},
                    {"label": "사진수", "source": "photoCount", "width": "1200"},
                    {"label": "사진ID", "source": "photoIds", "width": "3000"}
                  ]
                },
                {
                  "key": "photoSection",
                  "type": "PHOTO_TABLE",
                  "title": "현장 사진",
                  "photosPerRow": 2,
                  "imageSize": "MEDIUM",
                  "tableStyle": "ArchDoxPhotoTable",
                  "headerFill": "E8EEF5",
                  "borderColor": "5E6A75",
                  "fields": [
                    {"label": "설명", "source": "caption"},
                    {"label": "단계", "source": "stepCode"},
                    {"label": "체크항목", "source": "checklistItemLabel"}
                  ]
                }
              ]
            }
            $json$::jsonb
        ),
        (
            'DEMOLITION_DAILY_SUPERVISION_LOG',
            $json$
            {
              "flowId": "demolition-daily-supervision-log-writing",
              "title": "해체공사 감리일지 작성",
              "steps": [
                {
                  "code": "BASIC_INFO",
                  "title": "기본 정보",
                  "description": "공사일자, 날씨, 감리자, 해체작업 단계를 기록합니다.",
                  "stepType": "FORM",
                  "fields": [
                    {"key": "inspectionDate", "label": "공사일자", "type": "date", "required": true},
                    {"key": "weather", "label": "날씨", "type": "text", "required": false},
                    {"key": "supervisorName", "label": "공사감리자", "type": "text", "required": true},
                    {"key": "assistantSupervisorName", "label": "보조감리자", "type": "text", "required": false},
                    {"key": "stage", "label": "해체작업 단계", "type": "text", "required": false}
                  ]
                },
                {
                  "code": "DEMOLITION_DAILY_LOG",
                  "title": "감리 내용",
                  "description": "금일 작업범위, 감리 착안사항, 실제 감리내용을 기록합니다.",
                  "stepType": "FORM",
                  "fields": [
                    {"key": "workDescription", "label": "작업사항", "type": "textarea", "required": false},
                    {"key": "supervisionFocus", "label": "감리 착안사항", "type": "textarea", "required": false},
                    {"key": "supervisionContent", "label": "감리내용", "type": "textarea", "required": false},
                    {"key": "specialNotes", "label": "특기사항", "type": "textarea", "required": false},
                    {"key": "issueAndAction", "label": "지적사항 및 처리결과", "type": "textarea", "required": false}
                  ]
                },
                {"code": "CHECKLIST", "title": "감리 체크", "description": "해체공사 일지에 필요한 주요 확인 항목을 기록합니다.", "stepType": "CHECKLIST", "fields": []},
                {"code": "PHOTOS", "title": "사진 증거", "description": "금일 감리내용과 연결되는 사진을 첨부합니다.", "stepType": "PHOTO", "fields": []}
              ]
            }
            $json$::jsonb,
            $json$
            {
              "sections": [
                {
                  "key": "supervisionItemsSection",
                  "type": "CHECKLIST_TABLE",
                  "includeTitle": false,
                  "tableStyle": "ArchDoxInspectionTable",
                  "headerFill": "E8EEF5",
                  "borderColor": "5E6A75",
                  "fields": [
                    {"label": "구분", "source": "itemCode", "width": "1800"},
                    {"label": "감리 항목", "source": "label", "width": "3000"},
                    {"label": "결과", "source": "answer.value", "width": "1600"},
                    {"label": "사진", "source": "photoCount", "width": "1000"},
                    {"label": "감리내용", "source": "note", "width": "3600"}
                  ]
                },
                {
                  "key": "checklistPhotoSection",
                  "type": "CHECKLIST_PHOTO_TABLE",
                  "title": "체크 항목별 사진",
                  "includeTitle": true,
                  "tableStyle": "ArchDoxInspectionTable",
                  "headerFill": "EFE7C6",
                  "borderColor": "8A7A46",
                  "fields": [
                    {"label": "항목", "source": "label", "width": "4300"},
                    {"label": "사진수", "source": "photoCount", "width": "1200"},
                    {"label": "사진ID", "source": "photoIds", "width": "3600"}
                  ]
                },
                {
                  "key": "photoSection",
                  "type": "PHOTO_TABLE",
                  "title": "현장 사진",
                  "photosPerRow": 2,
                  "imageSize": "MEDIUM",
                  "tableStyle": "ArchDoxPhotoTable",
                  "headerFill": "E8EEF5",
                  "borderColor": "5E6A75",
                  "fields": [
                    {"label": "설명", "source": "caption"},
                    {"label": "단계", "source": "stepCode"},
                    {"label": "체크항목", "source": "checklistItemLabel"}
                  ]
                }
              ]
            }
            $json$::jsonb
        ),
        (
            'DEMOLITION_COMPLETION_REPORT',
            $json$
            {
              "flowId": "demolition-completion-report-writing",
              "title": "해체감리 완료보고서 작성",
              "steps": [
                {
                  "code": "BASIC_INFO",
                  "title": "기본 정보",
                  "description": "감리자, 감리사무소, 공사시공자, 용역명, 제출일을 기록합니다.",
                  "stepType": "FORM",
                  "fields": [
                    {"key": "supervisorName", "label": "감리자", "type": "text", "required": true},
                    {"key": "supervisorOfficeName", "label": "감리자 사무소", "type": "text", "required": false},
                    {"key": "contractorName", "label": "공사시공자", "type": "text", "required": false},
                    {"key": "serviceName", "label": "용역명", "type": "text", "required": true},
                    {"key": "reportDate", "label": "제출일", "type": "date", "required": false}
                  ]
                },
                {
                  "code": "SUPERVISOR_DEPLOYMENT",
                  "title": "감리자 배치 및 첨부 확인",
                  "description": "감리자 배치, 안전점검표, 감리일지, 첨부자료 제출 상태를 확인합니다.",
                  "stepType": "CHECKLIST",
                  "fields": []
                },
                {
                  "code": "REMARKS",
                  "title": "종합 의견",
                  "description": "완료 의견, 특기사항, 보완 필요사항을 기록합니다.",
                  "stepType": "FORM",
                  "fields": [
                    {"key": "comprehensiveOpinion", "label": "종합의견", "type": "textarea", "required": false},
                    {"key": "specialNotes", "label": "특기사항", "type": "textarea", "required": false}
                  ]
                },
                {"code": "PHOTOS", "title": "첨부 사진", "description": "완료보고서에 필요한 증빙 사진을 첨부합니다.", "stepType": "PHOTO", "fields": []}
              ]
            }
            $json$::jsonb,
            $json$
            {
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
                    {"label": "확인 내용", "source": "label", "width": "3300"},
                    {"label": "결과", "source": "answer.value", "width": "1600"},
                    {"label": "사진", "source": "photoCount", "width": "1000"},
                    {"label": "비고", "source": "note", "width": "3000"}
                  ]
                },
                {
                  "key": "photoSection",
                  "type": "PHOTO_TABLE",
                  "title": "첨부 사진",
                  "photosPerRow": 2,
                  "imageSize": "MEDIUM",
                  "tableStyle": "ArchDoxPhotoTable",
                  "headerFill": "E8EEF5",
                  "borderColor": "5E6A75",
                  "fields": [
                    {"label": "설명", "source": "caption"},
                    {"label": "단계", "source": "stepCode"}
                  ]
                }
              ]
            }
            $json$::jsonb
        ),
        (
            'CONSTRUCTION_DAILY_SUPERVISION_LOG',
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
                  "title": "공종별 감리 내용",
                  "description": "공종, 세부 공정, 층, 감리항목, 감리내용을 기록합니다.",
                  "stepType": "FORM",
                  "fields": [
                    {"key": "constructionTrade", "label": "공종", "type": "text", "required": false},
                    {"key": "detailedProcess", "label": "세부 공정", "type": "text", "required": false},
                    {"key": "floor", "label": "층/구역", "type": "text", "required": false},
                    {"key": "supervisionItem", "label": "감리항목", "type": "text", "required": false},
                    {"key": "supervisionContent", "label": "감리내용", "type": "textarea", "required": false},
                    {"key": "specialNotes", "label": "특기사항", "type": "textarea", "required": false},
                    {"key": "issueAndAction", "label": "지적사항 및 처리결과", "type": "textarea", "required": false}
                  ]
                },
                {"code": "CHECKLIST", "title": "감리 체크", "description": "공사감리일지에 필요한 주요 확인 항목을 기록합니다.", "stepType": "CHECKLIST", "fields": []},
                {"code": "PHOTOS", "title": "사진 증거", "description": "공종별 감리내용과 연결되는 사진을 첨부합니다.", "stepType": "PHOTO", "fields": []}
              ]
            }
            $json$::jsonb,
            $json$
            {
              "sections": [
                {
                  "key": "supervisionItemsSection",
                  "type": "CHECKLIST_TABLE",
                  "includeTitle": false,
                  "tableStyle": "ArchDoxInspectionTable",
                  "headerFill": "E8EEF5",
                  "borderColor": "5E6A75",
                  "fields": [
                    {"label": "공종/공정", "source": "answer.trade", "width": "2400"},
                    {"label": "감리 항목", "source": "label", "width": "3000"},
                    {"label": "결과", "source": "answer.value", "width": "1400"},
                    {"label": "사진", "source": "photoCount", "width": "1000"},
                    {"label": "감리내용", "source": "note", "width": "3600"}
                  ]
                },
                {
                  "key": "checklistPhotoSection",
                  "type": "CHECKLIST_PHOTO_TABLE",
                  "title": "체크 항목별 사진",
                  "includeTitle": true,
                  "tableStyle": "ArchDoxInspectionTable",
                  "headerFill": "EFE7C6",
                  "borderColor": "8A7A46",
                  "fields": [
                    {"label": "항목", "source": "label", "width": "4300"},
                    {"label": "사진수", "source": "photoCount", "width": "1200"},
                    {"label": "사진ID", "source": "photoIds", "width": "3600"}
                  ]
                },
                {
                  "key": "photoSection",
                  "type": "PHOTO_TABLE",
                  "title": "현장 사진",
                  "photosPerRow": 2,
                  "imageSize": "MEDIUM",
                  "tableStyle": "ArchDoxPhotoTable",
                  "headerFill": "E8EEF5",
                  "borderColor": "5E6A75",
                  "fields": [
                    {"label": "설명", "source": "caption"},
                    {"label": "단계", "source": "stepCode"},
                    {"label": "체크항목", "source": "checklistItemLabel"}
                  ]
                }
              ]
            }
            $json$::jsonb
        ),
        (
            'CONSTRUCTION_SUPERVISION_REPORT',
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
                {
                  "code": "REPORT_OPINION",
                  "title": "감리 의견 체크",
                  "description": "현장조사, 관계전문기술자 의견, 종합의견, 첨부자료를 확인합니다.",
                  "stepType": "CHECKLIST",
                  "fields": []
                },
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
                  "headerFill": "E8EEF5",
                  "borderColor": "5E6A75",
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
                  "headerFill": "E8EEF5",
                  "borderColor": "5E6A75",
                  "fields": [
                    {"label": "설명", "source": "caption"},
                    {"label": "단계", "source": "stepCode"}
                  ]
                }
              ]
            }
            $json$::jsonb
        )
)
update document_type_definitions definitions
set workflow_json = updates.workflow_json,
    output_layout_json = updates.output_layout_json,
    updated_at = now()
from document_type_updates updates
where definitions.office_id is null
  and definitions.code = updates.code;

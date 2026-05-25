import type { ReportFlowDefinition, ReportStepDefinition } from "../types";

export const reportFlowDefinition: ReportFlowDefinition = {
  flowId: "inspection-report-writing",
  title: "리포트 작성",
  steps: [
    {
      code: "BASIC_INFO",
      title: "기본 정보",
      description: "일자, 날씨, 담당자처럼 모든 문서가 공유하는 머리말 정보를 정리합니다.",
      stepType: "FORM",
      savePolicy: "ON_NAVIGATE",
      fields: [
        { key: "inspectionDate", label: "점검일", type: "date", required: true },
        { key: "weather", label: "날씨", placeholder: "맑음" },
        { key: "inspectorName", label: "담당자", placeholder: "홍길동", required: true }
      ]
    },
    {
      code: "WORK_SUMMARY",
      title: "작업 요약",
      description: "현장에서 확인한 작업 내용과 참여 인원을 기록합니다.",
      stepType: "FORM",
      savePolicy: "ON_NAVIGATE",
      fields: [
        {
          key: "workSummary",
          label: "작업 내용",
          type: "textarea",
          placeholder: "주요 작업과 확인 내용을 입력하세요."
        },
        { key: "workerCount", label: "작업 인원", type: "number", placeholder: "0" }
      ]
    },
    {
      code: "CHECKLIST",
      title: "점검 결과",
      description: "체크리스트 요약과 주요 이슈 수를 정리합니다. 세부 체크리스트는 아래 패널에서 관리합니다.",
      stepType: "CHECKLIST",
      savePolicy: "ON_NAVIGATE",
      fields: [
        {
          key: "checklistSummary",
          label: "점검 요약",
          type: "textarea",
          placeholder: "양호, 미흡, 보완 필요 항목을 요약하세요."
        },
        { key: "issueCount", label: "이슈 수", type: "number", placeholder: "0" }
      ]
    },
    {
      code: "PHOTOS",
      title: "현장 사진",
      description: "리포트에 포함할 사진을 올리고 원본 이관, 작업본, 썸네일 준비 상태를 확인합니다.",
      stepType: "PHOTO",
      savePolicy: "ON_NAVIGATE",
      fields: []
    },
    {
      code: "REMARKS",
      title: "비고와 조치",
      description: "특이사항, 다음 조치, 고객 전달 메모를 정리합니다.",
      stepType: "FORM",
      savePolicy: "ON_NAVIGATE",
      fields: [
        {
          key: "remarks",
          label: "비고",
          type: "textarea",
          placeholder: "특이사항이 없으면 비워둘 수 있습니다."
        },
        { key: "nextAction", label: "다음 조치", placeholder: "보완 요청, 재점검 예정 등" }
      ]
    }
  ]
};

export const reportStepDefinitions: ReportStepDefinition[] = reportFlowDefinition.steps;

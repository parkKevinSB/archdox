export type ReportTypeOption = {
  value: string;
  label: string;
};

export const reportTypeOptions: ReportTypeOption[] = [
  { value: "DEMOLITION_SAFETY_CHECKLIST", label: "해체공사 안전점검표" },
  { value: "DEMOLITION_DAILY_SUPERVISION_LOG", label: "해체공사 감리일지" },
  { value: "DEMOLITION_COMPLETION_REPORT", label: "해체감리완료 보고서" },
  { value: "CONSTRUCTION_DAILY_SUPERVISION_LOG", label: "공사감리일지" },
  { value: "CONSTRUCTION_SUPERVISION_REPORT", label: "감리보고서" },
  { value: "DAILY_SUPERVISION", label: "감리일지" },
  { value: "PERIODIC_SAFETY", label: "정기 안전점검" },
  { value: "FACILITY_CHECK", label: "시설 점검" }
];

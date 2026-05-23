export type ReportTypeOption = {
  value: string;
  label: string;
};

export const reportTypeOptions: ReportTypeOption[] = [
  { value: "DAILY_SUPERVISION", label: "감리일지" },
  { value: "PERIODIC_SAFETY", label: "정기 안전점검" },
  { value: "FACILITY_CHECK", label: "시설 점검" }
];

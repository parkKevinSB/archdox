import type { InspectionReport, InspectionStep, OfficeMember, Project, Site } from "../../types";

export type { InspectionReport, InspectionStep, OfficeMember, Project, Site };

export type ReportFormValues = {
  projectId: number;
  siteId: number;
  reportType: string;
  title: string;
};

export type ReportStepCode = "BASIC_INFO" | "WORK_SUMMARY" | "CHECKLIST" | "PHOTOS" | "REMARKS";

export type ReportStepType = "FORM" | "CHECKLIST" | "PHOTO";

export type ReportStepSavePolicy = "ON_NAVIGATE";

export type ReportStepField = {
  key: string;
  label: string;
  placeholder?: string;
  type?: "text" | "date" | "number" | "textarea";
};

export type ReportStepDefinition = {
  code: ReportStepCode;
  title: string;
  description: string;
  stepType: ReportStepType;
  savePolicy: ReportStepSavePolicy;
  fields: ReportStepField[];
};

export type ReportFlowDefinition = {
  flowId: string;
  title: string;
  steps: ReportStepDefinition[];
};

export type ReportWizardFormValues = Record<string, string>;

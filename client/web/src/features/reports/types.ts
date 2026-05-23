import type { InspectionReport, InspectionStep, OfficeMember, Project, Site } from "../../types";

export type { InspectionReport, InspectionStep, OfficeMember, Project, Site };

export type ReportFormValues = {
  projectId: number;
  siteId: number;
  reportType: string;
  title: string;
};

export type ReportStepCode = string;

export type ReportStepType = "FORM" | "CHECKLIST" | "PHOTO";

export type ReportStepSavePolicy = "ON_NAVIGATE";

export type ReportStepField = {
  key: string;
  label: string;
  placeholder?: string;
  required?: boolean;
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
  checklistSchemaCode?: string | null;
  checklistSchemaId?: number | null;
  checklistSchemaVersion?: number | null;
  definitionId?: number | null;
  flowId: string;
  officeId?: number;
  reportId?: number;
  reportType?: string;
  revisionId?: number | null;
  siteType?: string | null;
  source?: string;
  targetType?: string | null;
  title: string;
  version?: number | null;
  steps: ReportStepDefinition[];
};

export type ReportWizardFormValues = Record<string, string>;

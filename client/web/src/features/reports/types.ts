import type { InspectionReport, InspectionStep, OfficeMember, Project, Site } from "../../types";

export type { InspectionReport, InspectionStep, OfficeMember, Project, Site };

export type ReportFormValues = {
  projectId: number;
  siteId: number;
  reportType: string;
  title: string;
};

export type ReportStepCode = string;

export type ReportStepType = "FORM" | "CHECKLIST" | "PHOTO" | "DAILY_SUPERVISION_ITEMS" | (string & {});

export type ReportStepSavePolicy = "ON_NAVIGATE";

export type ReportStepField = {
  key: string;
  label: string;
  placeholder?: string;
  required?: boolean;
  type?: "text" | "date" | "number" | "textarea" | "json" | "hidden";
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

export type DocumentTypeDefinition = {
  id: number;
  officeId?: number | null;
  code: string;
  reportType: string;
  name: string;
  description?: string | null;
  category: string;
  defaultTemplateCode?: string | null;
  defaultTemplateStorageRef?: string | null;
  checklistSchemaCode?: string | null;
  defaultOutputFormat: string;
  displayOrder: number;
  steps: ReportStepDefinition[];
};

export type ReportWizardFormValues = Record<string, string>;

export type SupervisionCatalogItem = {
  basis?: string | null;
  code: string;
  name: string;
};

export type SupervisionCatalogTrade = {
  code: string;
  items: SupervisionCatalogItem[];
  name: string;
  processes?: string[];
  sourcePages?: number[];
};

export type SupervisionDomainCatalog = {
  catalogCode: string;
  catalogName: string;
  documentLayoutPolicy?: {
    defaultOfficialLayout?: {
      formRevision?: string;
      layoutVersion?: number;
      templateCode?: string;
    };
  };
  floorOptions?: string[];
  processOptions?: string[];
  source?: {
    documentTitle?: string;
    revisionLabel?: string;
  };
  status: string;
  trades: SupervisionCatalogTrade[];
  version: number;
};

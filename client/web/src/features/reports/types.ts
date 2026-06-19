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
  supervisionWorkMode?: string | null;
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
  checklistRows?: SupervisionCatalogChecklistRow[];
  code: string;
  name: string;
};

export type SupervisionCatalogChecklistRow = {
  basis?: string | null;
  code: string;
  label: string;
};

export type SupervisionCatalogProcessGroup = {
  code: string;
  items: SupervisionCatalogItem[];
  name: string;
  phaseCode?: string;
  sourcePages?: number[];
  subTradeCode?: string;
  subTradeName?: string;
  tradeCode?: string;
  workCategory?: string;
  workCategoryName?: string;
};

export type SupervisionCatalogPhase = {
  code: string;
  items: SupervisionCatalogItem[];
  name: string;
  processGroups?: SupervisionCatalogProcessGroup[];
  sourcePages?: number[];
};

export type SupervisionCatalogTrade = {
  code: string;
  discipline?: string;
  tradeGroupCode?: string;
  tradeGroupName?: string;
  items: SupervisionCatalogItem[];
  name: string;
  processGroups?: SupervisionCatalogProcessGroup[];
  processes?: string[];
  sourcePages?: number[];
  subTrades?: SupervisionCatalogSubTrade[];
};

export type SupervisionCatalogSubTrade = {
  code: string;
  name: string;
};

export type SupervisionCatalogTradeGroup = {
  code: string;
  name: string;
  tradeRefs?: SupervisionWorkModeTradeRef[];
};

export type SupervisionCatalogPhaseChecklistGroup = {
  code: string;
  name: string;
  phaseRefs?: SupervisionWorkModePhaseRef[];
};

export type SupervisionWorkModeProcessGroupRef = {
  code: string;
  itemRefs?: string[];
};

export type SupervisionWorkModeWorkCategoryRef = {
  code: string;
  name: string;
  processGroupRefs?: SupervisionWorkModeProcessGroupRef[];
};

export type SupervisionWorkModeSubTradeRef = {
  sourcePages?: number[];
  subTradeCode: string;
  subTradeName?: string;
  workCategories?: SupervisionWorkModeWorkCategoryRef[];
};

export type SupervisionWorkModeTradeRef = {
  sourcePages?: number[];
  subTradeRefs: SupervisionWorkModeSubTradeRef[];
  tradeCode: string;
};

export type SupervisionWorkModeTradeGroupRef = {
  tradeGroupCode: string;
  tradeGroupName?: string;
  tradeRefs?: SupervisionWorkModeTradeRef[];
};

export type SupervisionWorkModePhaseRef = {
  phaseCode: string;
  sourcePages?: number[];
  workCategories?: SupervisionWorkModeWorkCategoryRef[];
};

export type SupervisionWorkModePhaseChecklistGroupRef = {
  phaseChecklistGroupCode: string;
  phaseChecklistGroupName?: string;
  phaseRefs?: SupervisionWorkModePhaseRef[];
};

export type SupervisionCatalogAtom = {
  basis?: string | null;
  code: string;
  discipline?: string;
  name: string;
  tradeGroupCode?: string;
  tradeGroupName?: string;
  phaseCode?: string;
  processGroupCode?: string;
  rowRefs?: string[];
  subTradeCode?: string;
  subTradeName?: string;
  tradeCode?: string;
};

export type SupervisionCatalogCanonicalAtoms = {
  checklistRows?: Record<string, SupervisionCatalogChecklistRow>;
  constructionPhases?: Record<string, SupervisionCatalogAtom>;
  inspectionItems?: Record<string, SupervisionCatalogAtom>;
  phaseChecklistGroups?: Record<string, SupervisionCatalogAtom>;
  processGroups?: Record<string, SupervisionCatalogAtom>;
  subTrades?: Record<string, SupervisionCatalogAtom>;
  tradeGroups?: Record<string, SupervisionCatalogAtom>;
  trades?: Record<string, SupervisionCatalogAtom>;
};

export type SupervisionDomainCatalog = {
  catalogCode: string;
  catalogName: string;
  canonicalAtoms?: SupervisionCatalogCanonicalAtoms;
  selectedSupervisionWorkMode?: string;
  selectedSupervisionWorkModeName?: string;
  selectedSupervisionWorkModeCatalogCoverage?: {
    canWriteReports?: boolean;
    catalogDataSource?: string;
    dataPolicy?: string;
    message?: string;
    referencePages?: string;
    status?: string;
  };
  selectedSupervisionWorkModeCatalog?: {
    canWriteReports?: boolean;
    catalogDataSource?: string;
    code?: string;
    dataPolicy?: string;
    description?: string;
    message?: string;
    name?: string;
    phaseChecklistGroupRefs?: SupervisionWorkModePhaseChecklistGroupRef[];
    phaseRefs?: SupervisionWorkModePhaseRef[];
    referencePages?: string;
    status?: string;
    tradeGroupRefs?: SupervisionWorkModeTradeGroupRef[];
    tradeRefs?: SupervisionWorkModeTradeRef[];
  };
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
  supervisionWorkModes?: Array<{
    catalogDataSource?: string;
    code: string;
    description?: string;
    name: string;
    referencePages?: string;
    status?: string;
  }>;
  trades: SupervisionCatalogTrade[];
  version: number;
};

import type { InspectionReport, InspectionStep, Project } from "../../types";

export type { InspectionReport, InspectionStep, Project };

export type DocumentJobStatus = "REQUESTED" | "GENERATING" | "GENERATED" | "FAILED" | "CANCELLED";

export type DocumentJobProgressStep =
  | "QUEUED"
  | "VALIDATING"
  | "DISPATCHING"
  | "WAITING_FOR_AGENT"
  | "RENDERING"
  | "STORING_ARTIFACTS"
  | "GENERATED"
  | "FAILED";

export type DocumentWorkerType = "ARCHDOX_AGENT" | "CLOUD_API";
export type DocumentOutputFormat = "DOCX" | "HTML" | "HTML_AND_PDF" | "PDF" | "DOCX_AND_PDF" | "HWP" | "HWPX";
export type DocumentArtifactType = "DOCX" | "HTML" | "PDF" | "HWP" | "HWPX";
export type DocumentArtifactStorageKind = "API_LOCAL" | "ARCHDOX_AGENT" | "S3_COMPATIBLE";
export type DocumentDeliveryChannel = "DOWNLOAD";
export type DocumentDeliveryStatus = "REQUESTED" | "SENDING" | "COMPLETED" | "FAILED" | "CANCELLED";
export type ReportPreflightReviewStatus = "REQUESTED" | "RUNNING" | "PASSED" | "NEEDS_ATTENTION" | "FAILED";
export type ReportPreflightFindingSeverity = "INFO" | "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type ReportPreflightFindingResolutionStatus = "OPEN" | "RESOLVED" | "ACCEPTED";

export type DocumentSignatureInput = {
  signedByName: string;
  signedByRole?: string | null;
  signatureImageDataUrl: string;
  signatureImageMimeType?: string | null;
};

export type DocumentRenderOverrideInput = {
  path: string;
  value: string;
  label?: string | null;
  source?: string | null;
};

export type DocumentNarrativePolishFieldInput = {
  path: string;
  label?: string | null;
  value: string;
};

export type DocumentNarrativePolishSuggestionResponse = {
  path: string;
  label: string;
  originalText: string;
  polishedText: string;
  reason: string;
  source: "AI_HARNESS" | "RULE_BASED";
  confidence: "LOW" | "MEDIUM" | "HIGH";
  applicable: boolean;
};

export type DocumentNarrativePolishResponse = {
  status: "DRAFTED" | "NO_CHANGES" | "NEEDS_HUMAN_REVIEW";
  summary: string;
  providerCode: string;
  modelId: string;
  aiHarnessRunId: string;
  suggestions: DocumentNarrativePolishSuggestionResponse[];
};

export type DocumentNarrativeApplyResponse = {
  appliedCount: number;
  appliedPaths: string[];
};

export type DocumentArtifactResponse = {
  id: number;
  artifactType: DocumentArtifactType;
  storageKind: DocumentArtifactStorageKind;
  storageRef: string;
  fileName: string;
  mimeType: string;
  bytes: number;
  hashSha256?: string | null;
  createdAt: string;
};

export type DocumentJobResponse = {
  id: number;
  officeId: number;
  reportId: number;
  projectId: number;
  reportRevision: number;
  status: DocumentJobStatus;
  progressStep: DocumentJobProgressStep;
  progressPercent: number;
  progressMessage?: string | null;
  workerType: DocumentWorkerType;
  outputFormat: DocumentOutputFormat;
  errorCode?: string | null;
  errorMessage?: string | null;
  requestedAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  artifacts: DocumentArtifactResponse[];
};

export type DocumentDeliveryRequestResponse = {
  id: number;
  officeId: number;
  documentJobId: number;
  artifactId?: number | null;
  channel: DocumentDeliveryChannel;
  status: DocumentDeliveryStatus;
  recipientRef?: string | null;
  errorMessage?: string | null;
  downloadUrl?: string | null;
  requestedAt: string;
  completedAt?: string | null;
  updatedAt: string;
};

export type DocumentJobsByReport = Record<number, DocumentJobResponse[]>;
export type DocumentDeliveriesByJob = Record<number, DocumentDeliveryRequestResponse[]>;

export type ReportPreflightReviewRunResponse = {
  id: number;
  officeId: number;
  reportId: number;
  reportRevision: number;
  status: ReportPreflightReviewStatus;
  requestedBy?: number | null;
  terminalReason?: string | null;
  aiReviewPlanned: boolean;
  harnessRunId?: string | null;
  harnessStatus?: string | null;
  harnessAttempt: number;
  harnessTerminalReason?: string | null;
  aiProviderCode?: string | null;
  aiModelId?: string | null;
  requestedAt: string;
  updatedAt: string;
  completedAt?: string | null;
};

export type ReportPreflightReviewFindingResponse = {
  id: number;
  source: string;
  code: string;
  severity: ReportPreflightFindingSeverity;
  location?: string | null;
  message: string;
  evidence?: string | null;
  attributes: Record<string, string>;
  engineRunId?: string | null;
  engineStatus?: string | null;
  legalReferences: string[];
  legalReferenceDetails: ReportPreflightLegalReferenceResponse[];
  nextActions: string[];
  resolutionStatus: ReportPreflightFindingResolutionStatus;
  resolutionNote?: string | null;
  resolvedBy?: number | null;
  resolvedAt?: string | null;
  createdAt: string;
};

export type ReportPreflightLegalReferenceResponse = {
  referenceId: string;
  label: string;
  resolutionSource: string;
  bindingScope: string;
  bindingKey: string;
  relevance: string;
  catalogCode: string;
  catalogVersion?: number | null;
  checklistItemCode: string;
};

export type ReportPreflightRunsByReport = Record<number, ReportPreflightReviewRunResponse[]>;
export type ReportPreflightFindingsByRun = Record<number, ReportPreflightReviewFindingResponse[]>;

export type ChecklistPrintType = "TRADE" | "PHASE" | "ALL";

export type ChecklistPrintRowResponse = {
  workCategoryCode: string;
  workCategoryName: string;
  processCode: string;
  processName: string;
  inspectionItemCode: string;
  inspectionItemName: string;
  rowCode: string;
  rowLabel: string;
  basis: string;
  referenceNote: string;
  result: "" | "COMPLIANT" | "NON_COMPLIANT" | string;
  actionNote: string;
  checked: boolean;
};

export type ChecklistPrintDocumentResponse = {
  checklistType: ChecklistPrintType;
  title: string;
  documentNo: string;
  supervisionWorkMode: string;
  supervisionWorkModeName: string;
  tradeGroupCode: string;
  tradeGroupName: string;
  tradeCode: string;
  tradeName: string;
  subTradeCode: string;
  subTradeName: string;
  constructionPhaseCode: string;
  constructionPhaseName: string;
  floorArea: string;
  location: string;
  totalRowCount: number;
  checkedRowCount: number;
  rows: ChecklistPrintRowResponse[];
};

export type ChecklistPrintResponse = {
  reportId: number;
  reportNo: string;
  reportTitle: string;
  reportType: string;
  checklistType: ChecklistPrintType;
  checklistTypeName: string;
  documentCount: number;
  checkedRowCount: number;
  documents: ChecklistPrintDocumentResponse[];
  html: string;
};

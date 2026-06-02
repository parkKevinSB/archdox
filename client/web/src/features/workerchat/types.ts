export type WorkerChatMessageRole = "USER" | "ASSISTANT" | "SYSTEM";
export type WorkerChatMessageStatus = "PENDING" | "COMPLETED" | "FAILED" | "CANCELLED";
export type WorkerChatSessionStatus = "ACTIVE" | "COMPLETED" | "CANCELLED" | "EXPIRED";
export type WorkerChatStage =
  | "AWAITING_SITE"
  | "AWAITING_REPORT"
  | "REPORT_WORKING"
  | "REVIEWING"
  | "SIGNING"
  | "GENERATING_DOCUMENT"
  | "COMPLETED";

export type WorkerChatMessage = {
  id: number;
  sessionId: number;
  userId?: number | null;
  role: WorkerChatMessageRole;
  status: WorkerChatMessageStatus;
  content: string;
  workerRequestId?: string | null;
  workerActionType?: string | null;
  metadata: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
};

export type WorkerChatChoice = {
  kind: "SITE" | "REPORT";
  id: number;
  label: string;
  description?: string | null;
};

export type WorkerChatWorkflowField = {
  key: string;
  label: string;
  type?: string | null;
  placeholder?: string | null;
  required?: boolean;
};

export type WorkerChatWorkflowStep = {
  code: string;
  title: string;
  description?: string | null;
  stepType?: string | null;
  saved?: boolean;
  fields?: WorkerChatWorkflowField[];
};

export type WorkerChatPlannerProposal = {
  decision: "PROPOSE_ACTION" | "ASK_CLARIFICATION" | "NO_ACTION";
  actionType: string;
  requiresConfirmation: boolean;
  confidence: number;
  userMessage?: string | null;
  rationale?: string | null;
  payload: Record<string, unknown>;
};

export type WorkerChatWorkflowState = {
  report?: {
    id: number;
    status: string;
    contentRevision?: number;
    generationRevision?: number;
    submittedRevision?: number | null;
    generatedRevision?: number | null;
    lastDocumentJobId?: number | null;
  };
  latestPreflightRun?: {
    id: number;
    status: string;
    reportRevision?: number;
    terminalReason?: string | null;
    hasHarness?: boolean;
    harnessStatus?: string | null;
    requestedAt?: string;
    updatedAt?: string;
    completedAt?: string | null;
    findingCount?: number;
    openFindingCount?: number;
    blockingFindingCount?: number;
    findings?: Array<{
      id: number;
      source: string;
      code: string;
      severity: string;
      location?: string | null;
      message: string;
      resolutionStatus: string;
    }>;
  };
  latestDocumentJob?: {
    id: number;
    status: string;
    progressStep?: string;
    progressPercent?: number;
    progressMessage?: string | null;
    reportRevision?: number;
    outputFormat?: string;
    workerType?: string;
    errorCode?: string | null;
    errorMessage?: string | null;
    requestedAt?: string;
    updatedAt?: string;
    completedAt?: string | null;
  };
  preflightActive?: boolean;
  preflightPassedForCurrentRevision?: boolean;
  documentJobActive?: boolean;
  documentGenerated?: boolean;
  canRunPreflightReview?: boolean;
  canRequestDocumentGeneration?: boolean;
};

export type WorkerChatSession = {
  id: number;
  officeId: number;
  projectId: number;
  siteId?: number | null;
  reportId?: number | null;
  userId: number;
  status: WorkerChatSessionStatus;
  stage: WorkerChatStage;
  title: string;
  lastMessageAt?: string | null;
  completedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  workflowState: WorkerChatWorkflowState;
  messages: WorkerChatMessage[];
};

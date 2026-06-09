export type MembershipRole = "OWNER" | "ADMIN" | "MEMBER" | "VIEWER";

export type Office = {
  id: number;
  officeCode: string;
  displayName: string;
  type: string;
  planCode: string;
  role: MembershipRole;
};

export type ProjectStatus = "ACTIVE" | "ARCHIVED" | string;

export type Project = {
  id: number;
  officeId: number;
  name: string;
  address?: string | null;
  buildingType?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  status: ProjectStatus;
  manageAllowed?: boolean;
  structureManageAllowed?: boolean;
  reportCreateAllowed?: boolean;
};

export type ProjectAssignmentRole = "MANAGER" | "REPORT_WRITER" | "VIEWER";

export type AssignmentStatus = "ACTIVE" | "REMOVED" | string;

export type ProjectAssignment = {
  id: number;
  officeId: number;
  projectId: number;
  userId: number;
  email?: string | null;
  name?: string | null;
  role: ProjectAssignmentRole;
  status: AssignmentStatus;
  assignedBy?: number | null;
  assignedAt: string;
  updatedAt: string;
};

export type ProjectFormRequest = {
  name: string;
  address?: string | null;
  buildingType?: string | null;
  startDate?: string | null;
  endDate?: string | null;
};

export type MembershipStatus = "ACTIVE" | "SUSPENDED" | "LEFT";

export type OfficeInvitationStatus = "PENDING" | "ACCEPTED" | "CANCELLED" | "EXPIRED";

export type OfficeMember = {
  membershipId: number;
  userId: number;
  officeId: number;
  email: string;
  name: string;
  role: MembershipRole;
  status: MembershipStatus;
  joinedAt: string;
};

export type OfficeInvitation = {
  id: number;
  officeId: number;
  email: string;
  role: MembershipRole;
  status: OfficeInvitationStatus;
  invitedByUserId: number;
  acceptedByUserId?: number | null;
  tokenPreview: string;
  acceptToken?: string | null;
  acceptPath?: string | null;
  createdAt: string;
  expiresAt: string;
  acceptedAt?: string | null;
  cancelledAt?: string | null;
  updatedAt: string;
};

export type MeResponse = {
  id: number;
  email: string;
  name: string;
  offices: Office[];
};

export type AuthTokenResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
};

export type CountGroup = {
  total: number;
  byStatus: Record<string, number>;
};

export type OfficeOpsSummary = {
  officeId: number;
  agents: CountGroup;
  activeAgentSessions: number;
  inFlightAgentCommands: number;
  documentJobs: CountGroup;
  photos: CountGroup;
  photoOriginalPickups: CountGroup;
  documentDeliveries: CountGroup;
  generatedAt: string;
};

export type AgentSession = {
  id: number;
  agentId: number;
  apiInstanceId: string;
  websocketSessionId: string;
  status: string;
  connectedAt: string;
  lastSeenAt: string;
  disconnectedAt?: string | null;
  disconnectReason?: string | null;
};

export type Agent = {
  id: number;
  officeId: number;
  agentCode: string;
  deploymentMode: string;
  status: string;
  authMode: string;
  version?: string | null;
  lastSeenAt?: string | null;
  lastAuthenticatedAt?: string | null;
  pairedAt?: string | null;
  registeredAt: string;
  activeSessionCount: number;
  inFlightCommandCount: number;
  failedCommandCount: number;
  capabilities: Record<string, unknown>;
  storageProfile: Record<string, unknown>;
  activeSessions: AgentSession[];
};

export type AgentCommand = {
  id: number;
  agentId: number;
  agentCode: string;
  commandType: string;
  status: string;
  attemptCount: number;
  maxAttempts: number;
  createdAt: string;
  deliveredAt?: string | null;
  ackAt?: string | null;
  completedAt?: string | null;
  failedAt?: string | null;
  lastAttemptAt?: string | null;
  nextAttemptAt?: string | null;
  expiresAt: string;
  errorMessage?: string | null;
};

export type DocumentArtifact = {
  id: number;
  documentJobId: number;
  reportId: number;
  artifactType: string;
  storageKind: string;
  fileName: string;
  mimeType: string;
  bytes: number;
  hashSha256: string;
  createdAt: string;
};

export type DocumentJob = {
  id: number;
  officeId: number;
  reportId: number;
  projectId?: number | null;
  reportRevision: number;
  templateId?: number | null;
  status: string;
  progressStep: string;
  progressPercent: number;
  progressMessage?: string | null;
  requestedBy?: number | null;
  workerType: string;
  outputFormat: string;
  errorCode?: string | null;
  errorMessage?: string | null;
  requestedAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  updatedAt: string;
  artifacts: DocumentArtifact[];
};

export type PhotoAsset = {
  id: number;
  photoId: number;
  assetType: string;
  status: string;
  storageKind: string;
  mimeType: string;
  bytes?: number | null;
  width?: number | null;
  height?: number | null;
  hashSha256?: string | null;
  temporary: boolean;
  createdAt: string;
  uploadedAt?: string | null;
  pickedUpAt?: string | null;
  deletedAt?: string | null;
};

export type Photo = {
  id: number;
  officeId: number;
  projectId?: number | null;
  reportId?: number | null;
  stepCode?: string | null;
  checklistItemId?: number | null;
  captureKind: string;
  status: string;
  mimeType: string;
  width?: number | null;
  height?: number | null;
  bytes?: number | null;
  hashSha256: string;
  storageKind: string;
  uploadTarget: string;
  originalPickupStatus: string;
  requestedBy?: number | null;
  confirmedBy?: number | null;
  takenAt?: string | null;
  hasGps: boolean;
  createdAt: string;
  confirmedAt?: string | null;
  originalPickedUpAt?: string | null;
  originalTemporaryDeletedAt?: string | null;
  pickupErrorMessage?: string | null;
  updatedAt: string;
  assets: PhotoAsset[];
};

export type DocumentDelivery = {
  id: number;
  officeId: number;
  documentJobId: number;
  artifactId?: number | null;
  channel: string;
  status: string;
  recipientRef?: string | null;
  requestedBy?: number | null;
  errorMessage?: string | null;
  preparedStorageKind?: string | null;
  preparedExpiresAt?: string | null;
  downloadReadyAt?: string | null;
  agentCommandId?: number | null;
  requestedAt: string;
  completedAt?: string | null;
  updatedAt: string;
};

export type OperationEvent = {
  id: number;
  officeId?: number | null;
  severity: string;
  eventType: string;
  workflowType?: string | null;
  workflowKey?: string | null;
  resourceType?: string | null;
  resourceId?: string | null;
  actorUserId?: number | null;
  correlationId?: string | null;
  message: string;
  payload: Record<string, unknown>;
  createdAt: string;
};

export type WorkerGovernanceGroup = {
  actionType?: string | null;
  eventType?: string | null;
  reasonCode?: string | null;
  count: number;
};

export type WorkerActionDefinition = {
  actionType: string;
  owner: string;
  executorName: string;
  enabled: boolean;
  executorRegistered: boolean;
  readOnly: boolean;
  riskLevel: string;
  requiresApprovalByDefault: boolean;
  supportsDryRun: boolean;
  allowedSources: string[];
  requiredContextFields: string[];
  description: string;
};

export type WorkerGovernanceSummary = {
  from: string;
  to: string;
  officeId?: number | null;
  days: number;
  totalTraceEvents: number;
  requestReceived: number;
  policyAllowed: number;
  policyDenied: number;
  approvalRequired: number;
  actionSucceeded: number;
  actionFailed: number;
  actionRejected: number;
  actionCancelled: number;
  actionUnknown: number;
  catchRate: number;
  approvalRequiredRate: number;
  failureRate: number;
  dataPolicy: string;
  actionDefinitions: WorkerActionDefinition[];
  eventTypes: WorkerGovernanceGroup[];
  actionEvents: WorkerGovernanceGroup[];
  reasons: WorkerGovernanceGroup[];
  recentEvents: OperationEvent[];
};

export type FlowerRuntimeDump = {
  engineState: string;
  capturedAt: string;
  workerCount: number;
  activeFlowCount: number;
  workers: FlowerRuntimeWorker[];
};

export type FlowerRuntimeWorker = {
  name: string;
  state: string;
  intervalMillis: number;
  activeFlowCount: number;
  flows: FlowerRuntimeFlow[];
};

export type FlowerRuntimeFlow = {
  flowType: string;
  flowKey: string;
  state: string;
  currentStepId?: string | null;
  currentStepIndex: number;
  currentStepNo: number;
  failureType?: string | null;
  failureMessage?: string | null;
  executionContext: FlowerRuntimeExecutionContext;
  steps: FlowerRuntimeFlowStep[];
};

export type FlowerRuntimeExecutionContext = {
  tenantId?: string | null;
  userId?: string | null;
  sessionId?: string | null;
  runId?: string | null;
  traceId?: string | null;
  correlationId?: string | null;
};

export type FlowerRuntimeFlowStep = {
  index: number;
  stepId: string;
  stepType: string;
  current: boolean;
  guarded: boolean;
  recoverable: boolean;
  recoveryPolicy?: string | null;
};

export type WorkerApprovalRequest = {
  id: number;
  officeId?: number | null;
  status: string;
  workerRequestId: string;
  executionRequestId?: string | null;
  requestSource: string;
  command?: string | null;
  userId?: number | null;
  projectId?: number | null;
  siteId?: number | null;
  reportId?: number | null;
  documentJobId?: number | null;
  locale: string;
  actionType: string;
  actionOrigin: string;
  actionReason?: string | null;
  confidence: number;
  actionPayload: Record<string, unknown>;
  decisionCode?: string | null;
  decisionMessage?: string | null;
  decidedByUserId?: number | null;
  decisionReason?: string | null;
  requestedAt: string;
  expiresAt?: string | null;
  decidedAt?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ConfigDefinition = {
  id: number;
  officeId?: number | null;
  code: string;
  name: string;
  reportType?: string | null;
  status: string;
  createdBy?: number | null;
  createdAt: string;
  updatedAt: string;
};

export type DocumentTemplateRevision = {
  id: number;
  templateId: number;
  version: number;
  status: string;
  templateStorageKind?: string | null;
  templateStorageRef?: string | null;
  schema: Record<string, unknown>;
  composePolicy: Record<string, unknown>;
  aiPrompts: Record<string, unknown>;
  createdBy?: number | null;
  publishedBy?: number | null;
  createdAt: string;
  publishedAt?: string | null;
};

export type TemplateFieldDefinition = {
  key: string;
  label: string;
  category: string;
  source: string;
  example: string;
  description: string;
  reportTypes: string[];
};

export type TemplateFormPreset = {
  code: string;
  title: string;
  description: string;
  templateKind: string;
  customizationPolicy: string;
  renderingPolicy: string;
  reportTypes: string[];
  recommendedFields: string[];
  layoutSections: string[];
};

export type TemplateFieldCatalog = {
  reportType?: string | null;
  fields: TemplateFieldDefinition[];
  presets: TemplateFormPreset[];
};

export type ResolvedConfigPart = {
  source: string;
  definitionId?: number | null;
  revisionId?: number | null;
  code?: string | null;
  name?: string | null;
  reportType?: string | null;
  version?: number | null;
};

export type OfficeConfigOverride = {
  id: number;
  officeId: number;
  reportType: string;
  status: string;
  template: ResolvedConfigPart;
  workflow: ResolvedConfigPart;
  ruleSet: ResolvedConfigPart;
  outputLayout: ResolvedConfigPart;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  createdBy?: number | null;
  updatedBy?: number | null;
  createdAt: string;
  updatedAt: string;
};

export type PlatformAdminMe = {
  userId: number;
  email: string;
  role: string;
};

export type PlatformOpsSummary = {
  users: number;
  offices: number;
  agents: Record<string, number>;
  activeAgentSessions: number;
  agentCommands: Record<string, number>;
  documentJobs: Record<string, number>;
  photos: Record<string, number>;
  photoPickups: Record<string, number>;
  deliveries: Record<string, number>;
  generatedAt: string;
};

export type PlatformUserOps = {
  id: number;
  email: string;
  name: string;
  status: string;
  createdAt: string;
};

export type PlatformOfficeOps = {
  id: number;
  officeCode: string;
  displayName: string;
  type: string;
  planCode: string;
  status: string;
};

export type PlatformAgentOps = {
  id: number;
  officeId: number;
  agentCode: string;
  deploymentMode: string;
  status: string;
  version?: string | null;
  lastSeenAt?: string | null;
  capabilities: Record<string, unknown>;
  storageProfile: Record<string, unknown>;
};

export type PlatformAgentCommandOps = {
  id: number;
  officeId: number;
  agentId: number;
  agentCode: string;
  commandType: string;
  status: string;
  attemptCount: number;
  maxAttempts: number;
  createdAt: string;
  lastAttemptAt?: string | null;
  nextAttemptAt?: string | null;
  expiresAt: string;
  errorMessage?: string | null;
};

export type PlatformDocumentJobOps = {
  id: number;
  officeId: number;
  reportId: number;
  projectId?: number | null;
  reportRevision: number;
  status: string;
  progressStep: string;
  progressPercent: number;
  workerType: string;
  outputFormat: string;
  errorCode?: string | null;
  errorMessage?: string | null;
  requestedAt: string;
  updatedAt: string;
};

export type PlatformPhotoOps = {
  id: number;
  officeId: number;
  projectId?: number | null;
  reportId?: number | null;
  stepCode?: string | null;
  status: string;
  originalPickupStatus: string;
  uploadTarget: string;
  storageKind: string;
  bytes?: number | null;
  pickupErrorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PlatformDeliveryOps = {
  id: number;
  officeId: number;
  documentJobId: number;
  artifactId?: number | null;
  channel: string;
  status: string;
  agentCommandId?: number | null;
  errorMessage?: string | null;
  requestedAt: string;
  updatedAt: string;
};

export type PlatformHealthDetection = {
  stuckDocumentJobs: number;
  stuckAgentCommands: number;
  stuckPhotoPickups: number;
  stuckDeliveries: number;
  detectedAt: string;
  total: number;
  opsRunId?: number | null;
  incidentCount?: number;
  findingCount?: number;
};

export type PlatformOpsRun = {
  id: number;
  triggerType: string;
  status: string;
  startedByUserId?: number | null;
  incidentId?: number | null;
  inputSnapshotJson: Record<string, unknown>;
  aiHarnessRunId?: string | null;
  startedAt: string;
  completedAt?: string | null;
  failureCode?: string | null;
};

export type PlatformOpsIncident = {
  id: number;
  status: string;
  severity: string;
  category: string;
  title: string;
  summary: string;
  officeId?: number | null;
  primaryResourceType?: string | null;
  primaryResourceId?: string | null;
  firstSeenAt: string;
  lastSeenAt: string;
  resolvedAt?: string | null;
  createdByRunId?: number | null;
};

export type PlatformOpsFinding = {
  id: number;
  incidentId?: number | null;
  runId: number;
  officeId?: number | null;
  severity: string;
  source: string;
  code: string;
  category: string;
  title: string;
  message: string;
  resourceType?: string | null;
  resourceId?: string | null;
  workflowType?: string | null;
  workflowKey?: string | null;
  evidenceJson: Record<string, unknown>;
  recommendation?: string | null;
  createdAt: string;
};

export type LegalSyncRun = {
  id: number;
  triggerType: string;
  sourceCode: string;
  status: string;
  startedAt: string;
  completedAt?: string | null;
  failureCode?: string | null;
  summaryJson: Record<string, unknown>;
};

export type LegalChangeSet = {
  id: number;
  actId: number;
  syncRunId?: number | null;
  previousVersionId?: number | null;
  newVersionId: number;
  status: string;
  effectiveDate?: string | null;
  detectedAt: string;
  summary: string;
};

export type LegalChangeDigest = {
  id: number;
  changeSetId: number;
  status: string;
  source: string;
  title: string;
  summary: string;
  impactSummary?: string | null;
  affectedReportTypes: string[];
  affectedCatalogItems: string[];
  aiHarnessRunId?: string | null;
  effectiveDate?: string | null;
  detectedAt: string;
  publishedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  articleDiffs: LegalArticleDiff[];
};

export type LegalDigestAiDraft = {
  id: number;
  status: string;
  workerRequestId: string;
  digestId: number;
  changeSetId: number;
  dryRun: boolean;
  workerStatus: string;
  resultCode: string;
  aiHarnessRunId?: string | null;
  digestDraftStatus: string;
  title: string;
  summary: string;
  impactSummary: string;
  confidence: string;
  affectedReportTypes: string[];
  affectedCatalogItems: string[];
  keyArticles: string[];
  reviewNotes: string;
  publicationApplied: boolean;
  corpusMutated: boolean;
  digestMutated: boolean;
  generatedByUserId: number;
  generatedAt: string;
  reviewedByUserId?: number | null;
  reviewedAt?: string | null;
  appliedByUserId?: number | null;
  appliedAt?: string | null;
};

export type LegalArticleDiff = {
  id: number;
  articleId?: number | null;
  articleKey: string;
  articleNo?: string | null;
  articleTitle?: string | null;
  changeType: string;
  beforeArticleVersionId?: number | null;
  afterArticleVersionId?: number | null;
  beforeHash?: string | null;
  afterHash?: string | null;
  beforeTextPreview?: string | null;
  afterTextPreview?: string | null;
  legalVersionId?: number | null;
  sourceVersionKey?: string | null;
  effectiveDate?: string | null;
  sourceUrl?: string | null;
  publicSourceUrl?: string | null;
  diffSummary: string;
  createdAt: string;
};

export type LegalDigestRefreshResult = {
  inspectedChangeSets: number;
  createdDigests: number;
  refreshedDigests: number;
  skippedAiDigests: number;
  skippedMissingActs: number;
};

export type LegalOpenApiTarget = {
  target: string;
  query: string;
  expectedName: string;
  actCode: string;
  actType: string;
};

export type LegalOpenApiStatus = {
  enabled: boolean;
  ocConfigured: boolean;
  ready: boolean;
  sourceCode: string;
  baseUrl: string;
  userAgent: string;
  requestTimeoutMs: number;
  requestIntervalMs: number;
  maxAttempts: number;
  targetCount: number;
  estimatedRequestCount: number;
  targets: LegalOpenApiTarget[];
};

export type AiProviderType = "OPENAI" | "OLLAMA" | "GEMINI" | "ANTHROPIC" | "CUSTOM_HTTP";
export type AiProviderCredentialStatus = "DRAFT" | "ACTIVE" | "DISABLED";
export type AiCredentialDeliveryMode = "PROXY_ONLY" | "EPHEMERAL_TOKEN" | "DIRECT_SECRET";

export type AiProviderCredential = {
  id: number;
  providerCode: string;
  displayName: string;
  providerType: AiProviderType;
  status: AiProviderCredentialStatus;
  baseUrl?: string | null;
  defaultModel?: string | null;
  credentialVersion: number;
  apiKeyFingerprint?: string | null;
  apiKeyMasked?: string | null;
  apiKeyConfigured: boolean;
  createdAt: string;
  updatedAt: string;
  publishedAt?: string | null;
};

export type AiProviderConnectionTestResult = {
  providerId: number;
  providerCode: string;
  providerType: string;
  modelName: string;
  success: boolean;
  status: "SUCCEEDED" | "FAILED" | string;
  message: string;
  latencyMs?: number | null;
  finishReason?: string | null;
  responsePreview?: string | null;
  testedAt: string;
};

export type AiHarnessPolicy = {
  id?: number | null;
  policyKey: string;
  displayName: string;
  description?: string | null;
  enabled: boolean;
  providerCredentialId?: number | null;
  providerCode?: string | null;
  providerDisplayName?: string | null;
  providerType?: string | null;
  modelName?: string | null;
  effectiveModelName?: string | null;
  maxAttempts: number;
  timeoutSeconds: number;
  policyVersion: number;
  effectiveEnabled: boolean;
  effectiveMessage?: string | null;
  updatedAt?: string | null;
};

export type OfficeAiPolicy = {
  officeId: number;
  officeCode: string;
  officeName: string;
  aiEnabled: boolean;
  documentReviewAiEnabled: boolean;
  documentGenerationAiEnabled: boolean;
  preferredProviderCredentialId?: number | null;
  preferredProviderCode?: string | null;
  preferredProviderType?: string | null;
  credentialDeliveryMode: AiCredentialDeliveryMode;
  budgetEnforcementEnabled: boolean;
  monthlyBudgetAmount?: number | null;
  budgetCurrency: string;
  dailyCallLimit?: number | null;
  monthlyTokenLimit?: number | null;
  policyVersion: number;
  effectiveAiEnabled: boolean;
  effectiveMessage?: string | null;
  updatedAt?: string | null;
};

export type AiUsageGroup = {
  officeId?: number | null;
  feature: string;
  callCount: number;
  succeededCount: number;
  failedCount: number;
  inputTokens: number;
  outputTokens: number;
  estimatedTotalCost: number;
};

export type AiUsageSummary = {
  periodFrom: string;
  periodTo: string;
  currency: string;
  callCount: number;
  succeededCount: number;
  failedCount: number;
  inputTokens: number;
  outputTokens: number;
  estimatedTotalCost: number;
  groups: AiUsageGroup[];
};

export type AiWorkerEvaluationCase = {
  caseId: string;
  name: string;
  layer: string;
  status: string;
  automated: boolean;
  verification: string;
  evidence: string;
};

export type AiWorkerEvaluationGroup = {
  groupKey: string;
  displayName: string;
  layer: string;
  totalCases: number;
  automatedCases: number;
  passedCases: number;
  warningCases: number;
  failedCases: number;
  passRatePercent: number;
  cases: AiWorkerEvaluationCase[];
};

export type AiWorkerEvaluationSignal = {
  signalKey: string;
  displayName: string;
  status: string;
  layer: string;
  evidence: string;
};

export type AiWorkerEvaluationSummary = {
  generatedAt: string;
  evaluationMode: string;
  dataPolicy: string;
  totalCases: number;
  automatedCases: number;
  passedCases: number;
  warningCases: number;
  failedCases: number;
  passRatePercent: number;
  groups: AiWorkerEvaluationGroup[];
  signals: AiWorkerEvaluationSignal[];
};

export type AiWorkerEvaluationRun = {
  id: number;
  runKey: string;
  triggerType: string;
  status: string;
  evaluationMode: string;
  totalCases: number;
  automatedCases: number;
  passedCases: number;
  warningCases: number;
  failedCases: number;
  passRatePercent: number;
  groupCount: number;
  signalCount: number;
  warningSignalCount: number;
  failedSignalCount: number;
  triggeredByUserId?: number | null;
  triggeredByEmail?: string | null;
  createdAt: string;
  completedAt: string;
  summary: AiWorkerEvaluationSummary;
};

export type AiHarnessTraceEvent = {
  id: number;
  officeId?: number | null;
  harnessRunId: string;
  harnessId: string;
  eventType: string;
  status?: string | null;
  attempt?: number | null;
  modelId?: string | null;
  callId?: string | null;
  promptId?: string | null;
  promptVersion?: string | null;
  inputTokens?: number | null;
  outputTokens?: number | null;
  latencyMs?: number | null;
  findingCount?: number | null;
  validationValid?: boolean | null;
  validationErrorCount?: number | null;
  errorType?: string | null;
  message?: string | null;
  attributes: Record<string, unknown>;
  createdAt: string;
};

export type AiObservationMode = {
  enabled: boolean;
  maxEntries: number;
  ttlMinutes: number;
  maxPromptChars: number;
  maxResponseChars: number;
  currentEntryCount: number;
  updatedAt: string;
};

export type AiObservationMessage = {
  role: string;
  content: string;
};

export type AiObservation = {
  callId: string;
  status: string;
  providerCode: string;
  modelId: string;
  modelName: string;
  officeId?: number | null;
  feature?: string | null;
  workflowType?: string | null;
  workflowKey?: string | null;
  resourceType?: string | null;
  resourceId?: string | null;
  requestOptions: Record<string, unknown>;
  promptMessages: AiObservationMessage[];
  promptTruncated: boolean;
  responseText?: string | null;
  responseTruncated: boolean;
  inputTokens?: number | null;
  outputTokens?: number | null;
  latencyMs?: number | null;
  finishReason?: string | null;
  providerTrace: Record<string, string>;
  errorType?: string | null;
  errorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PlatformReportPreflightFinding = {
  id: number;
  officeId: number;
  reportId: number;
  reviewRunId: number;
  reviewRunStatus?: string | null;
  reviewRunTerminalReason?: string | null;
  source: string;
  code: string;
  severity: string;
  location?: string | null;
  message: string;
  evidence?: string | null;
  attributes: Record<string, string>;
  resolutionStatus: "OPEN" | "RESOLVED" | "ACCEPTED";
  resolutionNote?: string | null;
  resolvedBy?: number | null;
  resolvedAt?: string | null;
  createdAt: string;
};

export type EngineApiKey = {
  id: number;
  keyId: string;
  maskedKey: string;
  displayName: string;
  ownerUserId: number;
  officeId?: number | null;
  issuedByUserId: number;
  scopes: string[];
  dailyRequestUnitLimit: number;
  status: "ACTIVE" | "REVOKED" | string;
  expiresAt?: string | null;
  lastUsedAt?: string | null;
  revokedAt?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateEngineApiKeyResponse = {
  key: EngineApiKey;
  apiKey: string;
};

export type EngineApiUsageEvent = {
  id: number;
  apiKeyId: number;
  keyId: string;
  ownerUserId: number;
  officeId?: number | null;
  capability: string;
  operation: string;
  reviewSessionId?: string | null;
  status: string;
  requestUnits: number;
  metadata: Record<string, unknown>;
  createdAt: string;
};

export type EngineApiUsageGroup = {
  apiKeyId: number;
  keyId: string;
  ownerUserId: number;
  officeId?: number | null;
  capability: string;
  operation: string;
  eventCount: number;
  requestUnits: number;
  lastCalledAt: string;
};

export type EngineApiUsageSummary = {
  from: string;
  to: string;
  totalEventCount: number;
  totalRequestUnits: number;
  groups: EngineApiUsageGroup[];
};

export type AiModelCallLog = {
  id: number;
  callId: string;
  officeId?: number | null;
  providerCredentialId?: number | null;
  providerCode: string;
  providerType: string;
  modelId: string;
  modelName: string;
  feature?: string | null;
  workflowType?: string | null;
  workflowKey?: string | null;
  resourceType?: string | null;
  resourceId?: string | null;
  status: "SUCCEEDED" | "FAILED";
  inputTokens?: number | null;
  outputTokens?: number | null;
  latencyMs?: number | null;
  finishReason?: string | null;
  providerResponseId?: string | null;
  errorType?: string | null;
  errorMessage?: string | null;
  pricingRuleId?: number | null;
  costCurrency?: string | null;
  estimatedInputCost?: number | null;
  estimatedOutputCost?: number | null;
  estimatedTotalCost?: number | null;
  requestedAt: string;
  completedAt: string;
};

export type AiModelPricingRule = {
  id: number;
  providerCode: string;
  modelName: string;
  currency: string;
  inputTokenPricePerMillion: number;
  outputTokenPricePerMillion: number;
  status: "ACTIVE" | "DISABLED";
  createdByUserId?: number | null;
  createdAt: string;
  updatedAt: string;
  disabledAt?: string | null;
};

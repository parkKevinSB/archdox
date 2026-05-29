export type MembershipRole = "OWNER" | "ADMIN" | "MEMBER" | "VIEWER";

export type Office = {
  id: number;
  officeCode: string;
  displayName: string;
  type: string;
  planCode: string;
  role: MembershipRole;
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

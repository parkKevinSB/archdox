import type {
  Agent,
  AgentCommand,
  AgentSession,
  AiBudgetUsageSummary,
  AiHarnessPolicy,
  AiModelCallLog,
  AiModelPricingRule,
  AiUserBudgetOverride,
  AiHarnessTraceEvent,
  AiObservation,
  AiObservationMode,
  AiProviderConnectionTestResult,
  AiProviderCredential,
  AiUsageSummary,
  AiWorkerEvaluationRun,
  AiWorkerEvaluationSummary,
  AuthTokenResponse,
  ConfigDefinition,
  CreateEngineApiKeyResponse,
  DocumentDelivery,
  DocumentJob,
  EngineConnectBootstrapResponse,
  EngineConnectClient,
  EngineConnectClientType,
  EngineApiKey,
  EngineApiUsageEvent,
  EngineApiUsageSummary,
  FlowerRuntimeDump,
  LegalChangeDigest,
  LegalChangeSet,
  McpToolCatalogItem,
  McpLiveSmokeResult,
  LegalDomainBindingAutoGenerateResponse,
  LegalDomainBindingCoverage,
  LegalDomainBinding,
  LegalLawSearchResponse,
  LegalDigestAiDraft,
  LegalDigestRefreshResult,
  LegalOpenApiStatus,
  LegalSyncRun,
  DocumentTemplateRevision,
  MeResponse,
  MembershipRole,
  OfficeInvitation,
  OfficeOpsSummary,
  OfficeMember,
  OfficeConfigOverride,
  OperationEvent,
  PlatformAdminMe,
  PlatformAgentCommandOps,
  PlatformAgentOps,
  PlatformDeliveryOps,
  PlatformDocumentJobOps,
  PlatformHealthDetection,
  PlatformOfficeOps,
  PlatformOpsControlProfile,
  PlatformOpsDailyReport,
  PlatformOpsAutomationSettings,
  PlatformOpsFinding,
  PlatformOpsIncident,
  PlatformOpsRun,
  PlatformOpsSummary,
  PlatformPhotoOps,
  PlatformReportPreflightFinding,
  PlatformServerRuntimeHealth,
  PlatformUserOps,
  OfficeAiPolicy,
  Photo,
  Project,
  ProjectAssignment,
  ProjectAssignmentRole,
  ProjectFormRequest,
  TemplateFieldCatalog,
  WorkerApprovalRequest,
  WorkerGovernanceSummary
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

type TokenRefreshHandler = () => Promise<string | null>;

let tokenRefreshHandler: TokenRefreshHandler | null = null;

export function configureTokenRefresh(handler: TokenRefreshHandler | null) {
  tokenRefreshHandler = handler;
}

type RequestOptions = {
  token?: string | null;
  officeId?: number | null;
  method?: string;
  body?: unknown;
  query?: Record<string, string | number | undefined | null>;
};

type FormRequestOptions = Omit<RequestOptions, "body"> & {
  body: FormData;
};

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, options: RequestOptions = {}, retryOnUnauthorized = true): Promise<T> {
  const url = new URL(`${API_BASE}${path}`, window.location.origin);
  Object.entries(options.query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      url.searchParams.set(key, String(value));
    }
  });

  const headers = new Headers();
  headers.set("Accept", "application/json");
  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  if (options.token) {
    headers.set("Authorization", `Bearer ${options.token}`);
  }
  if (options.officeId) {
    headers.set("X-Office-Id", String(options.officeId));
  }

  let response = await fetch(url, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });

  if (response.status === 401 && retryOnUnauthorized && options.token && tokenRefreshHandler) {
    const refreshedToken = await tokenRefreshHandler();
    if (refreshedToken) {
      headers.set("Authorization", `Bearer ${refreshedToken}`);
      response = await fetch(url, {
        method: options.method ?? "GET",
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body)
      });
    }
  }

  if (!response.ok) {
    let message = `요청에 실패했습니다. (${response.status})`;
    try {
      const error = (await response.json()) as { code?: string; message?: string };
      message = error.message ?? message;
      if (response.status === 429 || error.code === "RATE_LIMITED") {
        message = "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
      }
    } catch {
      // Keep the generic message when the server does not return JSON.
    }
    throw new ApiError(response.status, message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

async function requestForm<T>(path: string, options: FormRequestOptions, retryOnUnauthorized = true): Promise<T> {
  const url = new URL(`${API_BASE}${path}`, window.location.origin);
  Object.entries(options.query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      url.searchParams.set(key, String(value));
    }
  });

  const headers = new Headers();
  headers.set("Accept", "application/json");
  if (options.token) {
    headers.set("Authorization", `Bearer ${options.token}`);
  }
  if (options.officeId) {
    headers.set("X-Office-Id", String(options.officeId));
  }

  let response = await fetch(url, {
    method: options.method ?? "POST",
    headers,
    body: options.body
  });

  if (response.status === 401 && retryOnUnauthorized && options.token && tokenRefreshHandler) {
    const refreshedToken = await tokenRefreshHandler();
    if (refreshedToken) {
      headers.set("Authorization", `Bearer ${refreshedToken}`);
      response = await fetch(url, {
        method: options.method ?? "POST",
        headers,
        body: options.body
      });
    }
  }

  if (!response.ok) {
    let message = `요청이 실패했습니다. (${response.status})`;
    try {
      const error = (await response.json()) as { code?: string; message?: string };
      message = error.message ?? message;
      if (response.status === 429 || error.code === "RATE_LIMITED") {
        message = "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
      }
    } catch {
      // Keep the generic message when the server does not return JSON.
    }
    throw new ApiError(response.status, message);
  }

  return (await response.json()) as T;
}

async function requestBlob(path: string, options: RequestOptions = {}, retryOnUnauthorized = true): Promise<Blob> {
  const url = new URL(`${API_BASE}${path}`, window.location.origin);
  Object.entries(options.query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      url.searchParams.set(key, String(value));
    }
  });

  const headers = new Headers();
  if (options.token) {
    headers.set("Authorization", `Bearer ${options.token}`);
  }
  if (options.officeId) {
    headers.set("X-Office-Id", String(options.officeId));
  }

  let response = await fetch(url, {
    method: options.method ?? "GET",
    headers
  });

  if (response.status === 401 && retryOnUnauthorized && options.token && tokenRefreshHandler) {
    const refreshedToken = await tokenRefreshHandler();
    if (refreshedToken) {
      headers.set("Authorization", `Bearer ${refreshedToken}`);
      response = await fetch(url, {
        method: options.method ?? "GET",
        headers
      });
    }
  }

  if (!response.ok) {
    let message = `요청이 실패했습니다. (${response.status})`;
    try {
      const error = (await response.json()) as { code?: string; message?: string };
      message = error.message ?? message;
      if (response.status === 429 || error.code === "RATE_LIMITED") {
        message = "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
      }
    } catch {
      // Binary endpoint may not return JSON.
    }
    throw new ApiError(response.status, message);
  }

  return response.blob();
}

export function login(email: string, password: string) {
  return request<AuthTokenResponse>("/api/v1/auth/login", {
    method: "POST",
    body: { email, password }
  }, false);
}

export function signup(email: string, password: string, name: string) {
  return request<AuthTokenResponse>("/api/v1/auth/signup", {
    method: "POST",
    body: { email, password, name, accountType: "PERSONAL" }
  }, false);
}

export function refreshAuthToken(refreshToken: string) {
  return request<AuthTokenResponse>("/api/v1/auth/refresh", {
    method: "POST",
    body: { refreshToken }
  }, false);
}

export function me(token: string) {
  return request<MeResponse>("/api/v1/me", { token });
}

export function getSummary(token: string, officeId: number) {
  return request<OfficeOpsSummary>("/api/v1/office-ops/summary", { token, officeId });
}

export function getAgents(token: string, officeId: number, limit = 50) {
  return request<Agent[]>("/api/v1/office-ops/agents", { token, officeId, query: { limit } });
}

export function getAgentSessions(token: string, officeId: number, limit = 50) {
  return request<AgentSession[]>("/api/v1/office-ops/agent-sessions", { token, officeId, query: { limit } });
}

export function getAgentCommands(token: string, officeId: number, limit = 50, status?: string) {
  return request<AgentCommand[]>("/api/v1/office-ops/agent-commands", {
    token,
    officeId,
    query: { limit, status }
  });
}

export function getDocumentJobs(token: string, officeId: number, limit = 50, status?: string) {
  return request<DocumentJob[]>("/api/v1/office-ops/document-jobs", {
    token,
    officeId,
    query: { limit, status }
  });
}

export function getPhotos(token: string, officeId: number, limit = 50, status?: string, originalPickupStatus?: string) {
  return request<Photo[]>("/api/v1/office-ops/photos", {
    token,
    officeId,
    query: { limit, status, originalPickupStatus }
  });
}

export function getDocumentDeliveries(token: string, officeId: number, limit = 50, status?: string) {
  return request<DocumentDelivery[]>("/api/v1/office-ops/document-deliveries", {
    token,
    officeId,
    query: { limit, status }
  });
}

export function getOperationEvents(token: string, officeId: number, limit = 50) {
  return request<OperationEvent[]>("/api/v1/operation-events", { token, officeId, query: { limit } });
}

export function getOfficeMembers(token: string, officeId: number) {
  return request<OfficeMember[]>(`/api/v1/offices/${officeId}/members`, { token, officeId });
}

export function addOfficeMember(token: string, officeId: number, body: { email: string; role: MembershipRole }) {
  return request<OfficeMember>(`/api/v1/offices/${officeId}/members`, {
    token,
    officeId,
    method: "POST",
    body
  });
}

export function updateOfficeMemberRole(
  token: string,
  officeId: number,
  memberUserId: number,
  body: { role: MembershipRole }
) {
  return request<OfficeMember>(`/api/v1/offices/${officeId}/members/${memberUserId}/role`, {
    token,
    officeId,
    method: "PATCH",
    body
  });
}

export function deactivateOfficeMember(token: string, officeId: number, memberUserId: number) {
  return request<OfficeMember>(`/api/v1/offices/${officeId}/members/${memberUserId}`, {
    token,
    officeId,
    method: "DELETE"
  });
}

export function getOfficeInvitations(token: string, officeId: number) {
  return request<OfficeInvitation[]>(`/api/v1/offices/${officeId}/invitations`, { token, officeId });
}

export function getProjects(token: string, officeId: number) {
  return request<Project[]>("/api/v1/projects", { token, officeId });
}

export function createProject(token: string, officeId: number, body: ProjectFormRequest) {
  return request<Project>("/api/v1/projects", {
    token,
    officeId,
    method: "POST",
    body
  });
}

export function updateProject(token: string, officeId: number, projectId: number, body: ProjectFormRequest) {
  return request<Project>(`/api/v1/projects/${projectId}`, {
    token,
    officeId,
    method: "PATCH",
    body
  });
}

export function archiveProject(token: string, officeId: number, projectId: number) {
  return request<Project>(`/api/v1/projects/${projectId}/archive`, {
    token,
    officeId,
    method: "POST"
  });
}

export function deleteProject(token: string, officeId: number, projectId: number) {
  return request<void>(`/api/v1/projects/${projectId}`, {
    token,
    officeId,
    method: "DELETE"
  });
}

export function getProjectAssignments(token: string, officeId: number, projectId: number) {
  return request<ProjectAssignment[]>(`/api/v1/projects/${projectId}/assignments`, { token, officeId });
}

export function upsertProjectAssignment(
  token: string,
  officeId: number,
  projectId: number,
  body: { userId: number; role: ProjectAssignmentRole }
) {
  return request<ProjectAssignment>(`/api/v1/projects/${projectId}/assignments`, {
    token,
    officeId,
    method: "PUT",
    body
  });
}

export function removeProjectAssignment(token: string, officeId: number, projectId: number, userId: number) {
  return request<ProjectAssignment>(`/api/v1/projects/${projectId}/assignments/${userId}`, {
    token,
    officeId,
    method: "DELETE"
  });
}

export function createOfficeInvitation(
  token: string,
  officeId: number,
  body: { email: string; role: MembershipRole; expiresInDays?: number | null }
) {
  return request<OfficeInvitation>(`/api/v1/offices/${officeId}/invitations`, {
    token,
    officeId,
    method: "POST",
    body
  });
}

export function cancelOfficeInvitation(token: string, officeId: number, invitationId: number) {
  return request<OfficeInvitation>(`/api/v1/offices/${officeId}/invitations/${invitationId}`, {
    token,
    officeId,
    method: "DELETE"
  });
}

export function acceptOfficeInvitation(token: string, invitationToken: string) {
  return request<OfficeMember>(`/api/v1/office-invitations/${encodeURIComponent(invitationToken)}/accept`, {
    token,
    method: "POST"
  });
}

export function getDocumentTemplates(token: string, officeId: number, reportType?: string) {
  return request<ConfigDefinition[]>("/api/v1/config/document-templates", {
    token,
    officeId,
    query: { reportType }
  });
}

export function getDocumentTemplateFields(token: string, officeId: number, reportType?: string) {
  return request<TemplateFieldCatalog>("/api/v1/config/document-template-fields", {
    token,
    officeId,
    query: { reportType }
  });
}

export function createDocumentTemplate(
  token: string,
  officeId: number,
  body: { code: string; name: string; reportType?: string | null }
) {
  return request<ConfigDefinition>("/api/v1/config/document-templates", {
    token,
    officeId,
    method: "POST",
    body
  });
}

export function getDocumentTemplateRevisions(token: string, officeId: number, templateId: number) {
  return request<DocumentTemplateRevision[]>(`/api/v1/config/document-templates/${templateId}/revisions`, {
    token,
    officeId
  });
}

export function createDocumentTemplateRevision(
  token: string,
  officeId: number,
  templateId: number,
  body: {
    schema?: Record<string, unknown>;
    composePolicy?: Record<string, unknown>;
    aiPrompts?: Record<string, unknown>;
  }
) {
  return request<DocumentTemplateRevision>(`/api/v1/config/document-templates/${templateId}/revisions`, {
    token,
    officeId,
    method: "POST",
    body
  });
}

export function uploadDocumentTemplateRevisionContent(
  token: string,
  officeId: number,
  revisionId: number,
  file: File
) {
  const body = new FormData();
  body.set("file", file);
  return requestForm<DocumentTemplateRevision>(`/api/v1/config/document-template-revisions/${revisionId}/content`, {
    token,
    officeId,
    method: "PUT",
    body
  });
}

export function publishDocumentTemplateRevision(token: string, officeId: number, revisionId: number) {
  return request<DocumentTemplateRevision>(`/api/v1/config/document-template-revisions/${revisionId}/publish`, {
    token,
    officeId,
    method: "POST"
  });
}

export function downloadDocumentTemplateRevisionContent(token: string, officeId: number, revisionId: number) {
  return requestBlob(`/api/v1/config/document-template-revisions/${revisionId}/content`, {
    token,
    officeId
  });
}

export function getOfficeConfigOverrides(token: string, officeId: number) {
  return request<OfficeConfigOverride[]>("/api/v1/config/office-overrides", { token, officeId });
}

export function updateOfficeConfigOverride(
  token: string,
  officeId: number,
  reportType: string,
  body: {
    templateRevisionId?: number | null;
    workflowRevisionId?: number | null;
    ruleSetRevisionId?: number | null;
    outputLayoutRevisionId?: number | null;
  }
) {
  return request<OfficeConfigOverride>(`/api/v1/config/office-overrides/${encodeURIComponent(reportType)}`, {
    token,
    officeId,
    method: "PUT",
    body
  });
}

export function getPlatformAdminMe(token: string) {
  return request<PlatformAdminMe>("/api/v1/platform-admin/me", { token });
}

export function getPlatformSummary(token: string) {
  return request<PlatformOpsSummary>("/api/v1/platform-admin/ops/summary", { token });
}

export function getPlatformServerRuntimeHealth(token: string) {
  return request<PlatformServerRuntimeHealth>("/api/v1/platform-admin/ops/server-runtime", { token });
}

export function updatePlatformServerRuntimeHealthSettings(
  token: string,
  body: {
    enabled?: boolean;
    checkIntervalMs?: number;
    cpuWarnPercent?: number;
    systemMemoryWarnPercent?: number;
    jvmHeapWarnPercent?: number;
    eventCooldownMs?: number;
  }
) {
  return request<PlatformServerRuntimeHealth["settings"]>("/api/v1/platform-admin/ops/server-runtime/settings", {
    token,
    method: "PUT",
    body
  });
}

export function getPlatformOpsAutomationSettings(token: string) {
  return request<PlatformOpsAutomationSettings>("/api/v1/platform-admin/ops/automation-settings", { token });
}

export function updatePlatformOpsAutomationSettings(
  token: string,
  body: Partial<PlatformOpsAutomationSettings>
) {
  return request<PlatformOpsAutomationSettings>("/api/v1/platform-admin/ops/automation-settings", {
    token,
    method: "PUT",
    body
  });
}

export function getPlatformUsers(token: string, limit = 50) {
  return request<PlatformUserOps[]>("/api/v1/platform-admin/ops/users", { token, query: { limit } });
}

export function getPlatformOffices(token: string, limit = 50) {
  return request<PlatformOfficeOps[]>("/api/v1/platform-admin/ops/offices", { token, query: { limit } });
}

export function getPlatformAgents(token: string, limit = 50) {
  return request<PlatformAgentOps[]>("/api/v1/platform-admin/ops/agents", { token, query: { limit } });
}

export function getPlatformCommands(token: string, limit = 50, status?: string) {
  return request<PlatformAgentCommandOps[]>("/api/v1/platform-admin/ops/agent-commands", {
    token,
    query: { limit, status }
  });
}

export function getPlatformDocumentJobs(token: string, limit = 50, status?: string) {
  return request<PlatformDocumentJobOps[]>("/api/v1/platform-admin/ops/document-jobs", {
    token,
    query: { limit, status }
  });
}

export function getPlatformPhotos(token: string, limit = 50, status?: string, pickupStatus?: string) {
  return request<PlatformPhotoOps[]>("/api/v1/platform-admin/ops/photos", {
    token,
    query: { limit, status, pickupStatus }
  });
}

export function getPlatformDeliveries(token: string, limit = 50, status?: string) {
  return request<PlatformDeliveryOps[]>("/api/v1/platform-admin/ops/deliveries", {
    token,
    query: { limit, status }
  });
}

export function getPlatformEvents(token: string, limit = 50) {
  return request<OperationEvent[]>("/api/v1/platform-admin/ops/events", {
    token,
    query: { limit }
  });
}

export function getPlatformWorkerGovernance(token: string, days = 7, recentLimit = 30, officeId?: number | null) {
  return request<WorkerGovernanceSummary>("/api/v1/platform-admin/ops/worker-governance", {
    token,
    query: { days, recentLimit, officeId }
  });
}

export function getPlatformWorkerApprovals(token: string, limit = 50, status?: string, officeId?: number | null) {
  return request<WorkerApprovalRequest[]>("/api/v1/platform-admin/ops/worker-approvals", {
    token,
    query: { limit, status, officeId }
  });
}

export function getPlatformFlowerRuntimeDump(token: string) {
  return request<FlowerRuntimeDump>("/api/v1/platform-admin/flower/dump", { token });
}

export function approvePlatformWorkerApproval(token: string, approvalRequestId: number, reason?: string) {
  return request<WorkerApprovalRequest>(`/api/v1/platform-admin/ops/worker-approvals/${approvalRequestId}/approve`, {
    token,
    method: "POST",
    body: { reason }
  });
}

export function rejectPlatformWorkerApproval(token: string, approvalRequestId: number, reason?: string) {
  return request<WorkerApprovalRequest>(`/api/v1/platform-admin/ops/worker-approvals/${approvalRequestId}/reject`, {
    token,
    method: "POST",
    body: { reason }
  });
}

export function detectPlatformStuckHealth(token: string) {
  return request<PlatformHealthDetection>("/api/v1/platform-admin/ops/health/detect-stuck", {
    token,
    method: "POST"
  });
}

export function getPlatformOpsRuns(token: string, limit = 50, status?: string) {
  return request<PlatformOpsRun[]>("/api/v1/platform-admin/ops/ops-runs", {
    token,
    query: { limit, status }
  });
}

export function getPlatformOpsIncidents(token: string, limit = 50, status?: string) {
  return request<PlatformOpsIncident[]>("/api/v1/platform-admin/ops/incidents", {
    token,
    query: { limit, status }
  });
}

export function getPlatformOpsFindings(token: string, limit = 50, incidentId?: number) {
  return request<PlatformOpsFinding[]>("/api/v1/platform-admin/ops/findings", {
    token,
    query: { limit, incidentId }
  });
}

export function getPlatformOpsDailyReports(token: string, limit = 20) {
  return request<PlatformOpsDailyReport[]>("/api/v1/platform-admin/ops/daily-reports", {
    token,
    query: { limit }
  });
}

export function getPlatformOpsControlProfiles(token: string, limit = 100, status?: string) {
  return request<PlatformOpsControlProfile[]>("/api/v1/platform-admin/ops/control-profiles", {
    token,
    query: { limit, status }
  });
}

export function createPlatformOpsControlProfile(
  token: string,
  body: {
    signalKind?: string;
    scopeType?: string;
    modelId?: string | null;
    signalText: string;
    severity?: string;
    iWeight?: number;
    sourceDailyReportId?: number | null;
    notes?: string | null;
  }
) {
  return request<PlatformOpsControlProfile>("/api/v1/platform-admin/ops/control-profiles", {
    token,
    method: "POST",
    body
  });
}

export function updatePlatformOpsControlProfile(
  token: string,
  profileId: number,
  body: {
    status?: string;
    severity?: string;
    iWeight?: number;
    notes?: string | null;
  }
) {
  return request<PlatformOpsControlProfile>(`/api/v1/platform-admin/ops/control-profiles/${profileId}`, {
    token,
    method: "PUT",
    body
  });
}

export function deletePlatformOpsControlProfile(token: string, profileId: number) {
  return request<void>(`/api/v1/platform-admin/ops/control-profiles/${profileId}`, {
    token,
    method: "DELETE"
  });
}

export function diagnosePlatformOpsIncident(token: string, incidentId: number) {
  return request<PlatformOpsRun>(`/api/v1/platform-admin/ops/incidents/${incidentId}/diagnose`, {
    token,
    method: "POST"
  });
}

export function startPlatformLegalOpenDataSync(token: string) {
  return request<LegalSyncRun>("/api/v1/platform-admin/legal/sync/open-data", {
    token,
    method: "POST"
  });
}

export function getPlatformLegalSyncRuns(token: string, limit = 50, sourceCode?: string) {
  return request<LegalSyncRun[]>("/api/v1/platform-admin/legal/sync-runs", {
    token,
    query: { limit, sourceCode }
  });
}

export function getPlatformLegalChangeSets(token: string, limit = 50) {
  return request<LegalChangeSet[]>("/api/v1/platform-admin/legal/change-sets", {
    token,
    query: { limit }
  });
}

export function getPlatformLegalChangeDigests(token: string, limit = 50) {
  return request<LegalChangeDigest[]>("/api/v1/platform-admin/legal/change-digests", {
    token,
    query: { limit }
  });
}

export function refreshPlatformLegalDeterministicDigests(token: string, limit = 200) {
  return request<LegalDigestRefreshResult>("/api/v1/platform-admin/legal/change-digests/refresh-deterministic", {
    token,
    method: "POST",
    query: { limit }
  });
}

export function generatePlatformLegalDigestAiDraft(token: string, digestId: number) {
  return request<LegalDigestAiDraft>(`/api/v1/platform-admin/legal/change-digests/${digestId}/ai-draft`, {
    token,
    method: "POST"
  });
}

export function getPlatformLegalDigestAiDrafts(token: string, digestId: number) {
  return request<LegalDigestAiDraft[]>(`/api/v1/platform-admin/legal/change-digests/${digestId}/ai-drafts`, {
    token
  });
}

export function applyPlatformLegalDigestAiDraft(token: string, digestId: number, draftId: number) {
  return request<LegalDigestAiDraft>(`/api/v1/platform-admin/legal/change-digests/${digestId}/ai-drafts/${draftId}/apply`, {
    token,
    method: "POST"
  });
}

export function approvePlatformLegalDigestAiDraft(token: string, digestId: number, draftId: number) {
  return request<LegalDigestAiDraft>(`/api/v1/platform-admin/legal/change-digests/${digestId}/ai-drafts/${draftId}/approve`, {
    token,
    method: "POST"
  });
}

export function rejectPlatformLegalDigestAiDraft(token: string, digestId: number, draftId: number) {
  return request<LegalDigestAiDraft>(`/api/v1/platform-admin/legal/change-digests/${digestId}/ai-drafts/${draftId}/reject`, {
    token,
    method: "POST"
  });
}

export function getPlatformLegalOpenApiStatus(token: string) {
  return request<LegalOpenApiStatus>("/api/v1/platform-admin/legal/open-api/status", { token });
}

export function getPlatformLegalDomainBindings(token: string, limit = 100) {
  return request<LegalDomainBinding[]>("/api/v1/platform-admin/legal/domain-bindings", {
    token,
    query: { limit }
  });
}

export function getPlatformLegalDomainBindingCoverage(token: string) {
  return request<LegalDomainBindingCoverage>("/api/v1/platform-admin/legal/domain-bindings/coverage/construction-supervision", {
    token
  });
}

export function searchPlatformLegalCorpus(
  token: string,
  query: {
    query?: string | null;
    actCode?: string | null;
    actName?: string | null;
    articleNo?: string | null;
    effectiveDate?: string | null;
    limit?: number;
  }
) {
  return request<LegalLawSearchResponse>("/api/v1/platform-admin/legal/law-search", {
    token,
    query
  });
}

export function createPlatformLegalDomainBinding(
  token: string,
  body: {
    bindingScope: string;
    bindingKey?: string | null;
    actId: number;
    articleId?: number | null;
    reportType?: string | null;
    catalogCode?: string | null;
    catalogVersion?: number | null;
    checklistItemCode?: string | null;
    relevance: string;
    status?: string | null;
    effectiveFrom?: string | null;
    effectiveTo?: string | null;
    notes?: string | null;
    metadataJson?: Record<string, unknown>;
  }
) {
  return request<LegalDomainBinding>("/api/v1/platform-admin/legal/domain-bindings", {
    token,
    method: "POST",
    body
  });
}

export function updatePlatformLegalDomainBinding(
  token: string,
  bindingId: number,
  body: {
    bindingScope: string;
    bindingKey?: string | null;
    actId: number;
    articleId?: number | null;
    reportType?: string | null;
    catalogCode?: string | null;
    catalogVersion?: number | null;
    checklistItemCode?: string | null;
    relevance: string;
    status?: string | null;
    effectiveFrom?: string | null;
    effectiveTo?: string | null;
    notes?: string | null;
    metadataJson?: Record<string, unknown>;
  }
) {
  return request<LegalDomainBinding>(`/api/v1/platform-admin/legal/domain-bindings/${bindingId}`, {
    token,
    method: "POST",
    body
  });
}

export function autoGeneratePlatformConstructionSupervisionLegalBindings(token: string) {
  return request<LegalDomainBindingAutoGenerateResponse>(
    "/api/v1/platform-admin/legal/domain-bindings/auto-generate/construction-supervision",
    {
      token,
      method: "POST"
    }
  );
}

export function deactivatePlatformLegalDomainBinding(token: string, bindingId: number) {
  return request<LegalDomainBinding>(`/api/v1/platform-admin/legal/domain-bindings/${bindingId}/deactivate`, {
    token,
    method: "POST"
  });
}

export function getPlatformEngineApiKeys(token: string) {
  return request<EngineApiKey[]>("/api/v1/platform-admin/engine/api-keys", { token });
}

export function getPlatformEngineUsageSummary(token: string) {
  return request<EngineApiUsageSummary>("/api/v1/platform-admin/engine/usage/summary", { token });
}

export function getPlatformEngineUsageEvents(token: string, limit = 100) {
  return request<EngineApiUsageEvent[]>("/api/v1/platform-admin/engine/usage/events", {
    token,
    query: { limit }
  });
}

export function createPlatformEngineApiKey(
  token: string,
  body: {
    displayName: string;
    ownerUserId: number;
    officeId?: number | null;
    scopes: string[];
    dailyRequestUnitLimit?: number | null;
    expiresAt?: string | null;
  }
) {
  return request<CreateEngineApiKeyResponse>("/api/v1/platform-admin/engine/api-keys", {
    token,
    method: "POST",
    body
  });
}

export function revokePlatformEngineApiKey(token: string, apiKeyId: number) {
  return request<EngineApiKey>(`/api/v1/platform-admin/engine/api-keys/${apiKeyId}/revoke`, {
    token,
    method: "POST"
  });
}

export function getEngineConnectClients(token: string) {
  return request<EngineConnectClient[]>("/api/v1/engine/connect/clients", { token });
}

export function createEngineConnectBootstrap(
  token: string,
  body: {
    clientType: EngineConnectClientType;
    displayName?: string | null;
    officeId?: number | null;
    expiresAt?: string | null;
  }
) {
  return request<EngineConnectBootstrapResponse>("/api/v1/engine/connect/bootstrap", {
    token,
    method: "POST",
    body
  });
}

export function getPlatformMcpToolCatalog(token: string) {
  return request<McpToolCatalogItem[]>("/api/v1/platform-admin/engine/mcp-tools", { token });
}

export function runPlatformMcpLiveSmoke(token: string, apiKey: string) {
  return request<McpLiveSmokeResult>("/api/v1/platform-admin/engine/mcp-smoke", {
    token,
    method: "POST",
    body: { apiKey }
  });
}

export function getPlatformAiProviders(token: string) {
  return request<AiProviderCredential[]>("/api/v1/platform-admin/ai/providers", { token });
}

export function createPlatformAiProvider(
  token: string,
  body: {
    providerCode: string;
    displayName: string;
    providerType: string;
    baseUrl?: string | null;
    defaultModel?: string | null;
    apiKey?: string | null;
  }
) {
  return request<AiProviderCredential>("/api/v1/platform-admin/ai/providers", {
    token,
    method: "POST",
    body
  });
}

export function updatePlatformAiProvider(
  token: string,
  providerId: number,
  body: {
    displayName: string;
    providerType: string;
    baseUrl?: string | null;
    defaultModel?: string | null;
    apiKey?: string | null;
  }
) {
  return request<AiProviderCredential>(`/api/v1/platform-admin/ai/providers/${providerId}`, {
    token,
    method: "PUT",
    body
  });
}

export function publishPlatformAiProvider(token: string, providerId: number) {
  return request<AiProviderCredential>(`/api/v1/platform-admin/ai/providers/${providerId}/publish`, {
    token,
    method: "POST"
  });
}

export function testPlatformAiProvider(token: string, providerId: number) {
  return request<AiProviderConnectionTestResult>(`/api/v1/platform-admin/ai/providers/${providerId}/test`, {
    token,
    method: "POST"
  });
}

export function getPlatformOfficeAiPolicies(token: string, limit = 100) {
  return request<OfficeAiPolicy[]>("/api/v1/platform-admin/ai/office-policies", {
    token,
    query: { limit }
  });
}

export function getPlatformAiHarnessPolicies(token: string) {
  return request<AiHarnessPolicy[]>("/api/v1/platform-admin/ai/harness-policies", { token });
}

export function updatePlatformAiHarnessPolicy(
  token: string,
  policyKey: string,
  body: {
    enabled: boolean;
    providerCredentialId?: number | null;
    modelName?: string | null;
    maxAttempts?: number | null;
    timeoutSeconds?: number | null;
    maxOutputTokens?: number | null;
    budgetEnforcementEnabled?: boolean;
    monthlyBudgetAmount?: number | null;
    budgetCurrency?: string | null;
    dailyCallLimit?: number | null;
    monthlyTokenLimit?: number | null;
  }
) {
  return request<AiHarnessPolicy>(`/api/v1/platform-admin/ai/harness-policies/${policyKey}`, {
    token,
    method: "PUT",
    body
  });
}

export function getPlatformAiCallLogs(token: string, limit = 100, status?: string) {
  return request<AiModelCallLog[]>("/api/v1/platform-admin/ai/call-logs", {
    token,
    query: { limit, status }
  });
}

export function getPlatformAiUsageSummary(token: string) {
  return request<AiUsageSummary>("/api/v1/platform-admin/ai/usage-summary", { token });
}

export function getPlatformAiBudgetUsageSummary(token: string) {
  return request<AiBudgetUsageSummary>("/api/v1/platform-admin/ai/budget-usage-summary", { token });
}

export function getPlatformAiUserBudgetOverrides(token: string, limit = 100) {
  return request<AiUserBudgetOverride[]>("/api/v1/platform-admin/ai/user-budget-overrides", {
    token,
    query: { limit }
  });
}

export function createPlatformAiUserBudgetOverride(
  token: string,
  body: {
    officeId: number;
    userId: number;
    dailyCallLimit?: number | null;
    monthlyTokenLimit?: number | null;
    monthlyBudgetAmount?: number | null;
    budgetCurrency?: string | null;
    expiresAt?: string | null;
    reason: string;
  }
) {
  return request<AiUserBudgetOverride>("/api/v1/platform-admin/ai/user-budget-overrides", {
    token,
    method: "POST",
    body
  });
}

export function disablePlatformAiUserBudgetOverride(token: string, overrideId: number, reason?: string | null) {
  return request<AiUserBudgetOverride>(`/api/v1/platform-admin/ai/user-budget-overrides/${overrideId}/disable`, {
    token,
    method: "POST",
    body: { reason: reason ?? null }
  });
}

export function getPlatformAiWorkerEvaluationSummary(token: string) {
  return request<AiWorkerEvaluationSummary>("/api/v1/platform-admin/ai/evaluation-summary", { token });
}

export function getPlatformAiWorkerEvaluationRuns(token: string, limit = 20) {
  return request<AiWorkerEvaluationRun[]>("/api/v1/platform-admin/ai/evaluation-runs", {
    token,
    query: { limit }
  });
}

export function createPlatformAiWorkerEvaluationRun(token: string) {
  return request<AiWorkerEvaluationRun>("/api/v1/platform-admin/ai/evaluation-runs", {
    token,
    method: "POST"
  });
}

export function createPlatformAiWorkerRuntimeEvaluationRun(token: string) {
  return request<AiWorkerEvaluationRun>("/api/v1/platform-admin/ai/evaluation-runs/runtime-probe", {
    token,
    method: "POST"
  });
}

export function createPlatformAiWorkerRuntimeScenarioRun(token: string) {
  return request<AiWorkerEvaluationRun>("/api/v1/platform-admin/ai/evaluation-runs/runtime-scenario", {
    token,
    method: "POST"
  });
}

export function getPlatformAiHarnessTraces(token: string, limit = 100, harnessRunId?: string) {
  return request<AiHarnessTraceEvent[]>("/api/v1/platform-admin/ai/harness-traces", {
    token,
    query: { limit, harnessRunId }
  });
}

export function getPlatformAiObservationMode(token: string) {
  return request<AiObservationMode>("/api/v1/platform-admin/ai/observation-mode", { token });
}

export function updatePlatformAiObservationMode(token: string, body: { enabled: boolean; clearExisting?: boolean }) {
  return request<AiObservationMode>("/api/v1/platform-admin/ai/observation-mode", {
    token,
    method: "PUT",
    body
  });
}

export function clearPlatformAiObservations(token: string) {
  return request<AiObservationMode>("/api/v1/platform-admin/ai/observations", {
    token,
    method: "DELETE"
  });
}

export function getPlatformAiObservations(token: string, limit = 50) {
  return request<AiObservation[]>("/api/v1/platform-admin/ai/observations", {
    token,
    query: { limit }
  });
}

export function getPlatformAiPreflightFindings(
  token: string,
  limit = 100,
  resolutionStatus?: string,
  severity?: string
) {
  return request<PlatformReportPreflightFinding[]>("/api/v1/platform-admin/ai/preflight-findings", {
    token,
    query: { limit, resolutionStatus, severity }
  });
}

export function getPlatformAiPricingRules(token: string, limit = 100, status?: string) {
  return request<AiModelPricingRule[]>("/api/v1/platform-admin/ai/pricing-rules", {
    token,
    query: { limit, status }
  });
}

export function createPlatformAiPricingRule(
  token: string,
  body: {
    providerCode: string;
    modelName: string;
    currency: string;
    inputTokenPricePerMillion: number;
    outputTokenPricePerMillion: number;
  }
) {
  return request<AiModelPricingRule>("/api/v1/platform-admin/ai/pricing-rules", {
    token,
    method: "POST",
    body
  });
}

export function disablePlatformAiPricingRule(token: string, pricingRuleId: number) {
  return request<AiModelPricingRule>(`/api/v1/platform-admin/ai/pricing-rules/${pricingRuleId}/disable`, {
    token,
    method: "POST"
  });
}

export function updatePlatformOfficeAiPolicy(
  token: string,
  officeId: number,
  body: {
    aiEnabled: boolean;
    documentReviewAiEnabled: boolean;
    documentGenerationAiEnabled: boolean;
    preferredProviderCredentialId?: number | null;
    credentialDeliveryMode: string;
    budgetEnforcementEnabled?: boolean;
    monthlyBudgetAmount?: number | null;
    budgetCurrency?: string | null;
    dailyCallLimit?: number | null;
    monthlyTokenLimit?: number | null;
    maxOutputTokens?: number | null;
    perUserDailyCallLimit?: number | null;
    perUserMonthlyTokenLimit?: number | null;
  }
) {
  return request<OfficeAiPolicy>(`/api/v1/platform-admin/ai/office-policies/${officeId}`, {
    token,
    method: "PUT",
    body
  });
}

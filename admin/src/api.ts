import type {
  Agent,
  AgentCommand,
  AgentSession,
  AuthTokenResponse,
  ConfigDefinition,
  DocumentDelivery,
  DocumentJob,
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
  PlatformOpsSummary,
  PlatformPhotoOps,
  PlatformUserOps,
  Photo,
  TemplateFieldCatalog
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

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

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
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

  const response = await fetch(url, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });

  if (!response.ok) {
    let message = `요청에 실패했습니다. (${response.status})`;
    try {
      const error = (await response.json()) as { message?: string };
      message = error.message ?? message;
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

async function requestForm<T>(path: string, options: FormRequestOptions): Promise<T> {
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

  const response = await fetch(url, {
    method: options.method ?? "POST",
    headers,
    body: options.body
  });

  if (!response.ok) {
    let message = `요청이 실패했습니다. (${response.status})`;
    try {
      const error = (await response.json()) as { message?: string };
      message = error.message ?? message;
    } catch {
      // Keep the generic message when the server does not return JSON.
    }
    throw new ApiError(response.status, message);
  }

  return (await response.json()) as T;
}

async function requestBlob(path: string, options: RequestOptions = {}): Promise<Blob> {
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

  const response = await fetch(url, {
    method: options.method ?? "GET",
    headers
  });

  if (!response.ok) {
    let message = `요청이 실패했습니다. (${response.status})`;
    try {
      const error = (await response.json()) as { message?: string };
      message = error.message ?? message;
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
  });
}

export function signup(email: string, password: string, name: string) {
  return request<AuthTokenResponse>("/api/v1/auth/signup", {
    method: "POST",
    body: { email, password, name, accountType: "PERSONAL" }
  });
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

export function detectPlatformStuckHealth(token: string) {
  return request<PlatformHealthDetection>("/api/v1/platform-admin/ops/health/detect-stuck", {
    token,
    method: "POST"
  });
}

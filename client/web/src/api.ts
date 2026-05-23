import type {
  AuthTokenResponse,
  InspectionReport,
  InspectionStep,
  InspectionTarget,
  MeResponse,
  OfficeInvitationPreview,
  OfficeMember,
  Project,
  Site
} from "./types";
import { apiErrorMessage, type ApiErrorPayload } from "./api/errorMessages";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

type RequestOptions = {
  token?: string | null;
  officeId?: number | null;
  method?: string;
  body?: unknown;
  query?: Record<string, string | number | undefined | null>;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string | null;
  readonly payload?: ApiErrorPayload | null;

  constructor(status: number, message: string, code?: string | null, payload?: ApiErrorPayload | null) {
    super(message);
    this.status = status;
    this.code = code;
    this.payload = payload;
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
    let message = apiErrorMessage(response.status);
    let payload: ApiErrorPayload | null = null;
    const contentType = response.headers.get("Content-Type") ?? "";
    try {
      if (contentType.includes("application/json")) {
        payload = (await response.json()) as ApiErrorPayload;
        message = apiErrorMessage(response.status, payload);
      } else {
        message = apiErrorMessage(response.status, null, await response.text());
      }
    } catch {
      // Keep the generic message when the server does not return a readable error body.
    }
    throw new ApiError(response.status, message, payload?.code, payload);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export function login(email: string, password: string) {
  return request<AuthTokenResponse>("/api/v1/auth/login", {
    method: "POST",
    body: { email, password }
  });
}

export function signup(
  email: string,
  password: string,
  name: string,
  options: {
    accountType?: "PERSONAL" | "OFFICE";
    officeCode?: string | null;
    invitationToken?: string | null;
  } = {}
) {
  return request<AuthTokenResponse>("/api/v1/auth/signup", {
    method: "POST",
    body: {
      email,
      password,
      name,
      accountType: options.accountType ?? "PERSONAL",
      officeCode: options.officeCode,
      invitationToken: options.invitationToken
    }
  });
}

export function me(token: string) {
  return request<MeResponse>("/api/v1/me", { token });
}

export function getOfficeInvitationPreview(invitationToken: string) {
  return request<OfficeInvitationPreview>(`/api/v1/office-invitations/${encodeURIComponent(invitationToken)}`);
}

export function acceptOfficeInvitation(token: string, invitationToken: string) {
  return request<OfficeMember>(`/api/v1/office-invitations/${encodeURIComponent(invitationToken)}/accept`, {
    token,
    method: "POST"
  });
}

export function getProjects(token: string, officeId: number) {
  return request<Project[]>("/api/v1/projects", { token, officeId });
}

export function getOfficeMembers(token: string, officeId: number) {
  return request<OfficeMember[]>(`/api/v1/offices/${officeId}/members`, { token, officeId });
}

export function createProject(
  token: string,
  officeId: number,
  body: {
    name: string;
    address?: string | null;
    buildingType?: string | null;
    startDate?: string | null;
    endDate?: string | null;
  }
) {
  return request<Project>("/api/v1/projects", {
    token,
    officeId,
    method: "POST",
    body
  });
}

export function getSites(token: string, officeId: number, projectId: number) {
  return request<Site[]>(`/api/v1/projects/${projectId}/sites`, { token, officeId });
}

export function createSite(
  token: string,
  officeId: number,
  projectId: number,
  body: {
    siteCode?: string | null;
    name: string;
    address?: string | null;
    siteType?: string | null;
    startDate?: string | null;
    endDate?: string | null;
  }
) {
  return request<Site>(`/api/v1/projects/${projectId}/sites`, {
    token,
    officeId,
    method: "POST",
    body
  });
}

export function getInspectionTargets(token: string, officeId: number, projectId: number, siteId: number) {
  return request<InspectionTarget[]>(`/api/v1/projects/${projectId}/sites/${siteId}/targets`, { token, officeId });
}

export function createInspectionTarget(
  token: string,
  officeId: number,
  projectId: number,
  siteId: number,
  body: {
    parentTargetId?: number | null;
    targetType: string;
    code?: string | null;
    name: string;
    address?: string | null;
    metadata?: Record<string, unknown> | null;
  }
) {
  return request<InspectionTarget>(`/api/v1/projects/${projectId}/sites/${siteId}/targets`, {
    token,
    officeId,
    method: "POST",
    body
  });
}

export function getInspectionReports(token: string, officeId: number) {
  return request<InspectionReport[]>("/api/v1/inspection-reports", { token, officeId });
}

export function createInspectionReport(
  token: string,
  officeId: number,
  body: {
    projectId: number;
    siteId?: number | null;
    reportType: string;
    title?: string | null;
    templateId?: number | null;
  }
) {
  return request<InspectionReport>("/api/v1/inspection-reports", {
    token,
    officeId,
    method: "POST",
    body
  });
}

export function saveInspectionStep(
  token: string,
  officeId: number,
  reportId: number,
  stepCode: string,
  payload: Record<string, unknown>
) {
  return request<InspectionStep>(`/api/v1/inspection-reports/${reportId}/steps/${encodeURIComponent(stepCode)}`, {
    token,
    officeId,
    method: "PUT",
    body: { payload }
  });
}

export function submitInspectionReport(token: string, officeId: number, reportId: number) {
  return request<InspectionReport>(`/api/v1/inspection-reports/${reportId}/submit`, {
    token,
    officeId,
    method: "POST"
  });
}

export function reopenInspectionReport(token: string, officeId: number, reportId: number) {
  return request<InspectionReport>(`/api/v1/inspection-reports/${reportId}/reopen`, {
    token,
    officeId,
    method: "POST"
  });
}

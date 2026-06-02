import { request } from "../../api/http";
import type { WorkerChatSession } from "./types";

export type SendWorkerChatMessagePayload = {
  content?: string;
  siteId?: number;
  reportId?: number;
  createSite?: {
    siteCode?: string;
    name: string;
    address?: string;
    siteType?: string;
  };
  createReport?: {
    siteId?: number;
    reportType?: string;
    title: string;
    templateId?: number;
  };
  updateReportStep?: {
    reportId?: number;
    stepCode?: string;
    payload: Record<string, unknown>;
  };
  submitReport?: {
    reportId?: number;
  };
  runPreflightReview?: {
    reportId?: number;
  };
  requestDocumentGeneration?: {
    reportId?: number;
    outputFormat?: string;
    workerType?: string;
  };
};

export function openWorkerChatSession(token: string, officeId: number, projectId: number) {
  return request<WorkerChatSession>(`/api/v1/projects/${projectId}/worker-chat`, { token, officeId });
}

export function cancelWorkerChatAction(token: string, officeId: number, projectId: number) {
  return request<WorkerChatSession>(`/api/v1/projects/${projectId}/worker-chat/cancel`, {
    token,
    officeId,
    method: "POST"
  });
}

export function sendWorkerChatMessage(
  token: string,
  officeId: number,
  projectId: number,
  payload: string | SendWorkerChatMessagePayload
) {
  const body = typeof payload === "string" ? { content: payload } : payload;
  return request<WorkerChatSession>(`/api/v1/projects/${projectId}/worker-chat/messages`, {
    token,
    officeId,
    method: "POST",
    body
  });
}

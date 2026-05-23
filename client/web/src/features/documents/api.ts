import { request } from "../../api/http";
import type {
  DocumentArtifactResponse,
  DocumentDeliveryRequestResponse,
  DocumentJobResponse,
  DocumentOutputFormat,
  DocumentWorkerType
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

export function createDocumentJob(
  token: string,
  officeId: number,
  reportId: number,
  body: {
    outputFormat?: DocumentOutputFormat;
    workerType?: DocumentWorkerType;
  } = {}
) {
  return request<DocumentJobResponse>(`/api/v1/inspection-reports/${reportId}/document-jobs`, {
    token,
    officeId,
    method: "POST",
    body
  });
}

export function listDocumentJobsByReport(token: string, officeId: number, reportId: number) {
  return request<DocumentJobResponse[]>(`/api/v1/inspection-reports/${reportId}/document-jobs`, {
    token,
    officeId
  });
}

export function createDocumentDeliveryRequest(
  token: string,
  officeId: number,
  jobId: number,
  artifactId?: number
) {
  return request<DocumentDeliveryRequestResponse>(`/api/v1/document-jobs/${jobId}/delivery-requests`, {
    token,
    officeId,
    method: "POST",
    body: {
      artifactId,
      channel: "DOWNLOAD"
    }
  });
}

export function listDocumentDeliveryRequestsByJob(token: string, officeId: number, jobId: number) {
  return request<DocumentDeliveryRequestResponse[]>(`/api/v1/document-jobs/${jobId}/delivery-requests`, {
    token,
    officeId
  });
}

export async function downloadDocumentUrl(
  token: string,
  officeId: number,
  downloadUrl: string,
  fileName: string
) {
  const response = await fetch(new URL(`${API_BASE}${downloadUrl}`, window.location.origin), {
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Office-Id": String(officeId)
    }
  });
  if (!response.ok) {
    throw new Error(`다운로드 요청에 실패했습니다. (${response.status})`);
  }
  const blob = await response.blob();
  const objectUrl = window.URL.createObjectURL(blob);
  try {
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
  } finally {
    window.URL.revokeObjectURL(objectUrl);
  }
}

export function defaultDownloadFileName(artifact: DocumentArtifactResponse) {
  return artifact.fileName || `archdox-document-${artifact.id}.${artifact.artifactType.toLowerCase()}`;
}
